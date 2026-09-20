package com.collect.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.collect.search.dto.SearchResult;
import com.collect.search.entity.Spider;
import com.collect.search.es.SpiderContentDoc;
import com.collect.search.mapper.SpiderMapper;
import com.collect.search.entity.UserFavorite;
import com.collect.search.mapper.UserFavoriteMapper;
import com.collect.common.security.LoginUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final SpiderMapper spiderMapper;
    private final UserFavoriteMapper userFavoriteMapper;

    @Value("${app.es.content-index:spider_content}")
    private String indexName;

    @SuppressWarnings("null")
    public Page<SearchResult> search(String keyword, Long spiderId, String spiderGroup, String tag,
                                     int current, int size) {
        PageRequest pageRequest = PageRequest.of(current - 1, size);
        Long userId = currentUserId();
        java.util.Map<String, List<String>> userTags = loadUserTags(userId);

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

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
        } else if (spiderId != null) {
            boolBuilder.must(m -> m.term(t -> t.field("spiderId").value(spiderId)));
        }

        if (tag != null && !tag.isBlank()) {
            List<String> taggedContentIds = userTags.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(tag))
                    .map(java.util.Map.Entry::getKey)
                    .toList();
            boolBuilder.must(m -> m.ids(i -> i.values(taggedContentIds)));
        }

        Query query = Query.of(q -> q.bool(boolBuilder.build()));

        boolean hasKeyword = keyword != null && !keyword.isBlank();

        SearchRequest.Builder reqBuilder = new SearchRequest.Builder()
                .index(indexName)
                .from(pageRequest.getPageNumber() * pageRequest.getPageSize())
                .size(pageRequest.getPageSize())
                .query(query);

        if (!hasKeyword) {
            reqBuilder.sort(s -> s.field(f -> f
                .field("updateTime")
                .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                .missing("_last")));
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
                if (hit.highlight() != null) {
                    sr.setTitleHl(hit.highlight().get("title") != null ? String.join(" ", hit.highlight().get("title")) : doc.getTitle());
                    sr.setContentHl(hit.highlight().get("content") != null ? String.join(" ", hit.highlight().get("content")) : snippet(doc.getContent()));
                } else {
                    sr.setTitleHl(doc.getTitle());
                    sr.setContentHl(snippet(doc.getContent()));
                }
                results.add(sr);
            }
            var totalObj = response.hits().total();
            long total = totalObj != null ? totalObj.value() : 0L;
            return new PageImpl<>(results, pageRequest, total);
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (e.response() != null
                    && e.response().error() != null
                    && "index_not_found_exception".equals(e.response().error().type())) {
                log.warn("ES 索引不存在，返回空结果: {}", indexName);
                return new PageImpl<>(List.of(), pageRequest, 0);
            }
            log.error("ES search failed", e);
            return new PageImpl<>(List.of(), pageRequest, 0);
        } catch (Exception e) {
            log.error("ES search failed", e);
            return new PageImpl<>(List.of(), pageRequest, 0);
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

    private String snippet(String content) {
        if (content == null) return null;
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    public SpiderContentDoc getById(String id) throws java.io.IOException {
        SpiderContentDoc doc = elasticsearchClient
                .get(g -> g.index(indexName).id(id), SpiderContentDoc.class)
                .source();
        if (doc != null) {
            doc.setTags(loadUserTags(currentUserId()).getOrDefault(id, List.of()));
        }
        return doc;
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
