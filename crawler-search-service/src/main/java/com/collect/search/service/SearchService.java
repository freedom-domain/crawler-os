package com.collect.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import co.elastic.clients.elasticsearch.core.OpenPointInTimeRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.PointInTimeReference;
import co.elastic.clients.elasticsearch._types.Time;
import com.collect.search.dto.SearchResult;
import com.collect.search.entity.Spider;
import com.collect.search.es.SpiderContentDoc;
import com.collect.search.mapper.SpiderMapper;
import com.collect.search.entity.UserFavorite;
import com.collect.search.mapper.UserFavoriteMapper;
import com.collect.search.entity.SearchHistory;
import com.collect.search.mapper.SearchHistoryMapper;
import com.collect.search.minio.MinioHelper;
import com.collect.common.security.LoginUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final SpiderMapper spiderMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final MinioHelper minioHelper;

    @Value("${app.es.content-index:spider_content}")
    private String indexName;

    @Value("${minio.html-bucket:crawler}")
    private String htmlBucket;

    @Value("${minio.js-bucket:crawler}")
    private String jsBucket;

    @Value("${minio.image-bucket:crawler}")
    private String imageBucket;

    /** 时间和 URL 排序字段数量；关键词搜索另外增加 _score。 */
    private static final int BASE_SORT_FIELD_COUNT = 2;

    /**
     * 搜索爬取的数据（PIT + search_after 游标式分页）。
     *
     * @param pitId       上一次查询返回的 PIT ID，首页传 null
     * @param searchAfter 上一次查询返回的完整排序游标（包括 PIT 的稳定分页值），首页传 null
     */
    @SuppressWarnings("null")
    public Page<SearchResult> search(String keyword, Long spiderId, String spiderGroup, String tag,
                                     boolean favoriteOnly, boolean hasImages, int size,
                                     String pitId, List<Object> searchAfter) {
        PageRequest pageRequest = PageRequest.of(0, size);
        Long userId = currentUserId();
        java.util.Map<String, List<String>> userTags = loadUserTags(userId);

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (userId == null) {
            List<Long> publicSpiderIds = spiderMapper.selectList(
                    new LambdaQueryWrapper<Spider>().eq(Spider::getIsPublic, 1)
            ).stream().map(Spider::getId).collect(Collectors.toList());
            if (publicSpiderIds.isEmpty()) {
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
            List<FieldValue> publicSpiderIdValues = publicSpiderIds.stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList());
            boolBuilder.must(m -> m.terms(t -> t.field("spiderId").terms(tt -> tt.value(publicSpiderIdValues))));
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword;
            boolBuilder.should(s -> s.match(mt -> mt.field("title").query(kw)));
            boolBuilder.should(s -> s.match(mt -> mt.field("content").query(kw)));
            boolBuilder.minimumShouldMatch("1");
        } else {
            boolBuilder.must(m -> m.matchAll(mt -> mt));
        }

        // 爬虫分组：从数据库读取该分组下的爬虫 ID，再按 spiderId 查询 ES
        if (spiderGroup != null && !spiderGroup.isBlank()) {
            List<Long> groupSpiderIds = spiderMapper.selectList(
                    new LambdaQueryWrapper<Spider>().eq(Spider::getGroup, spiderGroup)
            ).stream().map(s -> s.getId()).collect(Collectors.toList());
            if (groupSpiderIds.isEmpty()) {
                // 该分组下没有爬虫，直接返回空结果
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
            List<co.elastic.clients.elasticsearch._types.FieldValue> idValues = groupSpiderIds.stream()
                    .map(id -> co.elastic.clients.elasticsearch._types.FieldValue.of(id))
                    .collect(Collectors.toList());
            boolBuilder.must(m -> m.terms(t -> t.field("spiderId").terms(tt -> tt.value(idValues))));
        }
        // 分组和具体爬虫条件可以同时存在：前端选择爬虫时会同步带上所属分组，
        // 不能使用 else-if，否则具体爬虫条件会被分组条件覆盖。
        if (spiderId != null) {
            boolBuilder.must(m -> m.term(t -> t.field("spiderId").value(spiderId)));
        }

        if (hasImages) {
            // 直接依据图片数组是否包含实际元素过滤，空数组不计入结果。
            boolBuilder.must(m -> m.script(s -> s.script(script -> script
                    .source("doc.containsKey('images') && doc['images'].size() > 0"))));
        }

        if (favoriteOnly) {
            if (userId == null) {
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
            List<String> favoriteUrls = loadContentUrls(new java.util.ArrayList<>(userTags.keySet()));
            if (favoriteUrls.isEmpty()) {
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
            List<co.elastic.clients.elasticsearch._types.FieldValue> urlValues = favoriteUrls.stream()
                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                    .collect(Collectors.toList());
            boolBuilder.must(m -> m.terms(t -> t.field("url").terms(tt -> tt.value(urlValues))));
        }

        if (tag != null && !tag.isBlank()) {
            List<String> taggedContentIds = userTags.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(tag))
                    .map(java.util.Map.Entry::getKey)
                    .toList();
            List<String> taggedUrls = loadContentUrls(taggedContentIds);
            if (taggedUrls.isEmpty()) {
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
            List<co.elastic.clients.elasticsearch._types.FieldValue> urlValues = taggedUrls.stream()
                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                    .collect(Collectors.toList());
            boolBuilder.must(m -> m.terms(t -> t.field("url").terms(tt -> tt.value(urlValues))));
        }

        Query query = Query.of(q -> q.bool(boolBuilder.build()));

        boolean hasKeyword = keyword != null && !keyword.isBlank();

        // 打开或复用 PIT（Point In Time），保证翻页期间快照一致
        final String pit;
        if (pitId == null || pitId.isBlank()) {
            try {
                pit = elasticsearchClient.openPointInTime(
                        OpenPointInTimeRequest.of(o -> o
                                .index(indexName)
                                .keepAlive(Time.of(t -> t.time("5m")))))
                        .id();
            } catch (java.io.IOException e) {
                log.error("打开 PIT 失败", e);
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
        } else {
            pit = pitId;
        }

        // 使用 PIT 时不能同时指定 index（ES 校验：[indices] cannot be used with point in time）
        SearchRequest.Builder reqBuilder = new SearchRequest.Builder()
                .pit(PointInTimeReference.of(p -> p.id(pit)))
                .size(pageRequest.getPageSize())
                .query(query)
                .trackTotalHits(t -> t.enabled(true));

        // 关键词搜索按相关度优先；其余排序字段用于稳定 search_after 分页。
        if (hasKeyword) {
            reqBuilder.sort(s -> s.score(score -> score
                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
        }
        reqBuilder.sort(s -> s.field(f -> f
                .field("updateTime")
                .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                .missing("_last")));
        reqBuilder.sort(s -> s.field(f -> f
                .field("url")
                .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                .missing("_last")));

        int sortFieldCount = BASE_SORT_FIELD_COUNT + (hasKeyword ? 1 : 0);
        if (searchAfter != null && !searchAfter.isEmpty()) {
            List<FieldValue> afterValues = new java.util.ArrayList<>();
            try {
                for (Object value : searchAfter) {
                    afterValues.add(toSearchAfterValue(value));
                }
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring invalid search_after cursor", e);
                afterValues.clear();
            }
            if (afterValues.size() >= sortFieldCount) {
                reqBuilder.searchAfter(afterValues);
            } else {
                log.warn("Ignoring invalid search_after cursor with {} values", afterValues.size());
            }
        }

        if (hasKeyword) {
            reqBuilder.highlight(h -> h
                    .preTags("<em>")
                    .postTags("</em>")
                    .fragmentSize(200)
                    .numberOfFragments(3)
                    .fields("title", f -> f)
                    .fields("content", f -> f)
            );
        }

        SearchRequest request = reqBuilder.build();

        try {
            SearchResponse<SpiderContentDoc> response = elasticsearchClient
                    .search(request, SpiderContentDoc.class);
            List<SearchResult> results = new java.util.ArrayList<>();
            // 根据爬虫 ID 从数据库带出爬虫分组
            java.util.Map<Long, String> spiderGroupMap = loadSpiderGroups(
                    response.hits().hits().stream()
                            .map(Hit::source)
                            .filter(java.util.Objects::nonNull)
                            .map(SpiderContentDoc::getSpiderId)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList()));
            for (Hit<SpiderContentDoc> hit : response.hits().hits()) {
                SpiderContentDoc doc = hit.source();
                if (doc == null) continue;
                SearchResult sr = new SearchResult();
                sr.setId(doc.getId());
                sr.setTitle(doc.getTitle());
                sr.setContent(doc.getContent());
                sr.setUrl(doc.getUrl());
                sr.setAuthor(doc.getAuthor());
                sr.setSpiderId(doc.getSpiderId());
                sr.setSpiderName(doc.getSpiderName());
                sr.setSpiderGroup(doc.getSpiderId() != null ? spiderGroupMap.get(doc.getSpiderId()) : null);
                sr.setSourceType(doc.getSourceType());
                sr.setCrawlTime(doc.getCrawlTime());
                sr.setUpdateTime(doc.getUpdateTime());
                sr.setImages(doc.getImages());
                sr.setTags(userTags.getOrDefault(doc.getId(), List.of()));
                sr.setFavorited(userTags.containsKey(doc.getId()));
                if (hit.highlight() != null) {
                    sr.setTitleHl(hit.highlight().get("title") != null ? String.join(" ", hit.highlight().get("title")) : doc.getTitle());
                    sr.setContentHl(hit.highlight().get("content") != null ? String.join(" ", hit.highlight().get("content")) : snippet(doc.getContent()));
                } else {
                    sr.setTitleHl(doc.getTitle());
                    sr.setContentHl(snippet(doc.getContent()));
                }
                // Preserve every PIT sort value, including its implicit _shard_doc tiebreaker.
                sr.setSortValues(hit.sort() != null
                        ? hit.sort().stream().map(this::toSortValue).toList()
                        : List.of());
                results.add(sr);
            }
            var totalObj = response.hits().total();
            long total = totalObj != null ? totalObj.value() : 0L;
            // 返回自定义 Page 子类，携带 PIT ID 供前端下一页使用
            String nextPit = response.pitId() != null ? response.pitId() : pit;
            return new PagedSearchResult(results, pageRequest, total, nextPit);
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (e.response() != null
                    && e.response().error() != null
                    && "index_not_found_exception".equals(e.response().error().type())) {
                log.warn("ES 索引不存在，返回空结果: {}", indexName);
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
                var error = e.response() != null ? e.response().error() : null;
                String rootCause = error != null && error.rootCause() != null && !error.rootCause().isEmpty()
                    ? error.rootCause().stream()
                    .map(cause -> cause.type() + ": " + cause.reason())
                    .collect(Collectors.joining("; "))
                    : error != null && error.causedBy() != null
                    ? error.causedBy().type() + ": " + error.causedBy().reason()
                    : null;
                log.error("ES search failed: type={}, reason={}, rootCause={}",
                    error != null ? error.type() : null,
                    error != null ? error.reason() : e.getMessage(), rootCause, e);
            return new PageImpl<>(List.of(), pageRequest, 0);
        } catch (Exception e) {
            log.error("ES search failed", e);
            return new PageImpl<>(List.of(), pageRequest, 0);
        }
    }

    private Object toSortValue(FieldValue value) {
        return switch (value._kind()) {
            case Double -> value.doubleValue();
            case Long -> value.longValue();
            case Boolean -> value.booleanValue();
            case String -> value.stringValue();
            case Null -> null;
            default -> value.toString();
        };
    }

    private FieldValue toSearchAfterValue(Object value) {
        if (value == null) {
            return FieldValue.NULL;
        }
        if (value instanceof Boolean booleanValue) {
            return FieldValue.of(booleanValue);
        }
        if (value instanceof Number number) {
            if (number instanceof Float || number instanceof Double || number instanceof java.math.BigDecimal) {
                return FieldValue.of(number.doubleValue());
            }
            return FieldValue.of(number.longValue());
        }
        if (value instanceof String stringValue) {
            return FieldValue.of(stringValue);
        }
        throw new IllegalArgumentException("Unsupported search_after value type: " + value.getClass().getName());
    }

    /**
     * 携带 PIT ID 的 Page 实现，序列化后前端可从 pitId 字段读取游标上下文。
     */
    @lombok.Getter
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class PagedSearchResult extends PageImpl<SearchResult> {
        private final String pitId;

        public PagedSearchResult(List<SearchResult> content, PageRequest pageRequest, long total, String pitId) {
            super(content, pageRequest, total);
            this.pitId = pitId;
        }

        /**
         * 使用稳定的字段名返回命中总数，避免不同 Page 序列化配置导致前端读取不一致。
         */
        public long getTotal() {
            return getTotalElements();
        }
    }

    /**
     * 根据爬虫 ID 批量查询爬虫分组，返回 spiderId -> group 的映射。
     */
    @SuppressWarnings("null")
    private java.util.Map<Long, String> loadSpiderGroups(List<Long> spiderIds) {
        if (spiderIds == null || spiderIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<Spider> spiders = spiderMapper.selectList(
                new LambdaQueryWrapper<Spider>().in(Spider::getId, spiderIds));
        java.util.Map<Long, String> map = new java.util.HashMap<>();
        for (Spider s : spiders) {
            map.put(s.getId(), s.getGroup());
        }
        return map;
    }

    private List<String> loadContentUrls(List<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return List.of();
        }
        List<String> urls = new java.util.ArrayList<>();
        for (String contentId : contentIds) {
            try {
                SpiderContentDoc doc = elasticsearchClient
                        .get(g -> g.index(indexName).id(contentId), SpiderContentDoc.class)
                        .source();
                if (doc != null && doc.getUrl() != null && !doc.getUrl().isBlank()) {
                    urls.add(doc.getUrl());
                }
            } catch (Exception e) {
                log.debug("读取标签对应内容 URL 失败: contentId={}", contentId, e);
            }
        }
        return urls.stream().distinct().toList();
    }

    private String snippet(String content) {
        if (content == null) return null;
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    public SpiderContentDoc getById(String id) throws java.io.IOException {
        SpiderContentDoc doc = elasticsearchClient
                .get(g -> g.index(indexName).id(id), SpiderContentDoc.class)
                .source();
        if (doc != null && currentUserId() == null) {
            if (doc.getSpiderId() == null
                    || spiderMapper.selectCount(new LambdaQueryWrapper<Spider>()
                            .eq(Spider::getId, doc.getSpiderId())
                            .eq(Spider::getIsPublic, 1)) == 0) {
                return null;
            }
        }
        if (doc != null) {
            doc.setTags(loadUserTags(currentUserId()).getOrDefault(id, List.of()));
            // HTML 原文存储在 MinIO（对象名 = html/{md5(url)}.html），详情时按需读取
            if (doc.getUrl() != null && !doc.getUrl().isBlank()) {
                String rawHtml = minioHelper.readHtml(htmlBucket, "html/" + md5(doc.getUrl()) + ".html");
                doc.setRawHtml(rewriteStaticResourceUrls(rawHtml, doc.getUrl()));
            }
        }
        return doc;
    }

    public void deleteImage(String contentId, String objectName) throws java.io.IOException {
        if (objectName == null || objectName.isBlank()) {
            throw new com.collect.common.exception.BizException("图片地址不能为空");
        }
        SpiderContentDoc doc = elasticsearchClient
                .get(g -> g.index(indexName).id(contentId), SpiderContentDoc.class)
                .source();
        if (doc == null || doc.getImages() == null || !doc.getImages().contains(objectName)) {
            throw new com.collect.common.exception.BizException("图片不存在");
        }
        List<String> remaining = doc.getImages().stream()
                .filter(image -> !objectName.equals(image))
                .toList();
        doc.setImages(remaining);
        elasticsearchClient.index(i -> i.index(indexName).id(contentId).document(doc));
        try {
            minioHelper.removeObject(imageBucket, objectName);
        } catch (Exception e) {
            log.warn("删除 MinIO 图片失败: bucket={}, object={}", imageBucket, objectName, e);
        }
    }

    private String rewriteStaticResourceUrls(String html, String baseUrl) {
        if (html == null || baseUrl == null || baseUrl.isBlank()) return html;
        String rewritten = rewriteResourceAttribute(html, baseUrl, "script", "src", false);
        return rewriteResourceAttribute(rewritten, baseUrl, "link", "href", true);
    }

    private String rewriteResourceAttribute(String html, String baseUrl, String tag, String attribute, boolean stylesheet) {
        Pattern pattern = Pattern.compile("(<" + tag + "\\b[^>]*\\s" + attribute + "\\s*=\\s*[\\\"'])([^\\\"']+)([\\\"'])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tagText = matcher.group(0);
            if (stylesheet && !tagText.toLowerCase().matches(".*\\brel\\s*=\\s*[\\\"'][^\\\"']*stylesheet[^\\\"']*[\\\"'].*")) {
                continue;
            }
            String source = matcher.group(2).trim();
            String absolute = resolveUrl(baseUrl, source);
            if (absolute == null || absolute.startsWith("data:") || absolute.startsWith("javascript:")) continue;
            String extension = stylesheet ? ".css" : resourceExtension(absolute, ".js");
            String objectName = (stylesheet ? "css/" : "js/") + md5(absolute) + extension;
            String resourceUrl = "/api/file/resource?bucket=" +
                    java.net.URLEncoder.encode(jsBucket, StandardCharsets.UTF_8) +
                    "&objectName=" + java.net.URLEncoder.encode(objectName, StandardCharsets.UTF_8);
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + resourceUrl + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveUrl(String baseUrl, String source) {
        try {
            return new URI(baseUrl).resolve(source).toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private String resourceExtension(String url, String fallback) {
        String path = url;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) path = path.substring(0, queryIndex);
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot > slash && dot < path.length() - 1 ? path.substring(dot) : fallback;
    }

    private String md5(String input) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("null")
    public void updateTags(String id, List<String> tags) throws java.io.IOException {
        if (elasticsearchClient.get(g -> g.index(indexName).id(id), SpiderContentDoc.class).source() == null) {
            throw new com.collect.common.exception.BizException("数据不存在");
        }
        Long userId = LoginUtils.getUserId();
        UserFavorite favorite = userFavoriteMapper.selectOne(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getContentId, id));
        if (favorite == null) {
            favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setContentId(id);
            favorite.setTags(JSON.toJSONString(tags != null ? tags : List.of()));
            userFavoriteMapper.insert(favorite);
        } else {
            favorite.setTags(JSON.toJSONString(tags != null ? tags : List.of()));
            userFavoriteMapper.updateById(favorite);
        }
    }

    @SuppressWarnings("null")
        public Page<SearchResult> favorites(int current, int size, String title, String url,
                            String spiderName, String tag) throws java.io.IOException {
        Long userId = LoginUtils.getUserId();
        PageRequest pageRequest = PageRequest.of(current - 1, size);
        List<UserFavorite> favorites = userFavoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>()
            .eq(UserFavorite::getUserId, userId)
            .orderByDesc(UserFavorite::getCreateTime));

        List<SearchResult> results = new java.util.ArrayList<>();
        String normalizedTitle = normalizeFilter(title);
        String normalizedUrl = normalizeFilter(url);
        String normalizedSpiderName = normalizeFilter(spiderName);
        String normalizedTag = normalizeFilter(tag);
        for (UserFavorite favorite : favorites) {
            SpiderContentDoc doc = elasticsearchClient
                    .get(g -> g.index(indexName).id(favorite.getContentId()), SpiderContentDoc.class)
                    .source();
            if (doc == null) continue;

            SearchResult result = new SearchResult();
            result.setId(doc.getId());
            result.setTitle(doc.getTitle());
            result.setContent(doc.getContent());
            result.setUrl(doc.getUrl());
            result.setAuthor(doc.getAuthor());
            result.setSpiderId(doc.getSpiderId());
            result.setSpiderName(doc.getSpiderName());
            result.setSourceType(doc.getSourceType());
            result.setCrawlTime(doc.getCrawlTime());
            result.setUpdateTime(doc.getUpdateTime());
            result.setImages(doc.getImages());
            result.setTags(parseTags(favorite.getTags()));
            result.setFavorited(true);
            if (!matchesFavorite(result, normalizedTitle, normalizedUrl, normalizedSpiderName, normalizedTag)) {
                continue;
            }
            results.add(result);
        }
        int fromIndex = Math.min((current - 1) * size, results.size());
        int toIndex = Math.min(fromIndex + size, results.size());
        return new PageImpl<>(results.subList(fromIndex, toIndex), pageRequest, results.size());
    }

    private boolean matchesFavorite(SearchResult result, String title, String url, String spiderName, String tag) {
        return (title.isEmpty() || containsIgnoreCase(result.getTitle(), title))
                && (url.isEmpty() || containsIgnoreCase(result.getUrl(), url))
                && (spiderName.isEmpty() || containsIgnoreCase(result.getSpiderName(), spiderName))
                && (tag.isEmpty() || result.getTags().stream().anyMatch(value -> containsIgnoreCase(value, tag)));
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(keyword);
    }

    @SuppressWarnings("null")
    public void deleteFavorite(String contentId) {
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, LoginUtils.getUserId())
                .eq(UserFavorite::getContentId, contentId));
    }

    @SuppressWarnings("null")
    public void favorite(String contentId) throws java.io.IOException {
        SpiderContentDoc doc = elasticsearchClient
                .get(g -> g.index(indexName).id(contentId), SpiderContentDoc.class)
                .source();
        if (doc == null) {
            throw new com.collect.common.exception.BizException("数据不存在");
        }
        Long userId = LoginUtils.getUserId();
        UserFavorite favorite = userFavoriteMapper.selectOne(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getContentId, contentId));
        if (favorite == null) {
            favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setContentId(contentId);
            favorite.setTags(JSON.toJSONString(List.of()));
            userFavoriteMapper.insert(favorite);
        }
    }

    public void delete(String id) throws java.io.IOException {
        elasticsearchClient.delete(d -> d.index(indexName).id(id));
    }

    private Long currentUserId() {
        try {
            return LoginUtils.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("null")
    public void recordHistory(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        Long userId = currentUserId();
        if (userId == null) {
            return;
        }
        String normalizedKeyword = keyword.trim();
        searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
            .eq(SearchHistory::getUserId, userId)
            .eq(SearchHistory::getKeyword, normalizedKeyword));

        SearchHistory history = new SearchHistory();
        history.setUserId(userId);
        history.setKeyword(normalizedKeyword);
        searchHistoryMapper.insert(history);
    }

    @SuppressWarnings("null")
    public void syncHistory(List<String> keywords) {
        if (keywords == null) {
            return;
        }
        java.util.List<String> uniqueKeywords = new java.util.ArrayList<>(keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::trim)
                .distinct()
            .toList());
        java.util.Collections.reverse(uniqueKeywords);
        uniqueKeywords.forEach(this::recordHistory);
    }

    @SuppressWarnings("null")
    public List<SearchHistory> history() {
        List<SearchHistory> records = searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, LoginUtils.getUserId())
                .eq(SearchHistory::getDeleted, 0)
                .orderByDesc(SearchHistory::getCreateTime)
                .last("LIMIT 200"));
        return new java.util.ArrayList<>(records.stream()
            .collect(Collectors.toMap(SearchHistory::getKeyword, item -> item,
                (first, duplicate) -> first, java.util.LinkedHashMap::new))
            .values())
            .stream()
            .limit(20)
            .toList();
    }

    @SuppressWarnings("null")
    public void clearHistory() {
        searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, LoginUtils.getUserId()));
    }

    @SuppressWarnings("null")
    public void deleteHistory(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, LoginUtils.getUserId())
                .eq(SearchHistory::getKeyword, keyword.trim()));
    }

    @SuppressWarnings("null")
    private java.util.Map<String, List<String>> loadUserTags(Long userId) {
        if (userId == null) {
            return java.util.Collections.emptyMap();
        }
        return userFavoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserFavorite::getContentId,
                        favorite -> parseTags(favorite.getTags()),
                        (left, right) -> right));
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseArray(tags, String.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
