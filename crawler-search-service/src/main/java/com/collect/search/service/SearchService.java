package com.collect.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.collect.search.dto.SearchResult;
import com.collect.search.es.SpiderContentDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.es.content-index:spider_content}")
    private String indexName;

    public SearchService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public Page<SearchResult> search(String keyword, Long spiderId,
                                     int current, int size) {
        PageRequest pageRequest = PageRequest.of(current - 1, size);

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword;
            boolBuilder.should(s -> s.match(mt -> mt.field("title").query(kw)));
            boolBuilder.should(s -> s.match(mt -> mt.field("content").query(kw)));
            boolBuilder.minimumShouldMatch("1");
        } else {
            boolBuilder.must(m -> m.matchAll(mt -> mt));
        }

        if (spiderId != null) {
            boolBuilder.must(m -> m.term(t -> t.field("spiderId").value(spiderId)));
        }

        Query query = Query.of(q -> q.bool(boolBuilder.build()));

        SearchRequest.Builder reqBuilder = new SearchRequest.Builder()
                .index(indexName)
                .from(pageRequest.getPageNumber() * pageRequest.getPageSize())
                .size(pageRequest.getPageSize())
                .query(query);

        if (keyword != null && !keyword.isBlank()) {
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
                sr.setSourceType(doc.getSourceType());
                sr.setCrawlTime(doc.getCrawlTime());
                sr.setImages(doc.getImages());
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

    private String snippet(String content) {
        if (content == null) return null;
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    public SpiderContentDoc getById(String id) throws java.io.IOException {
        return elasticsearchClient
                .get(g -> g.index(indexName).id(id), SpiderContentDoc.class)
                .source();
    }

    public void delete(String id) throws java.io.IOException {
        elasticsearchClient.delete(d -> d.index(indexName).id(id));
    }
}
