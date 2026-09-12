package com.collect.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.collect.search.es.SpiderContentDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchClient elasticsearchClient;

    public Page<SpiderContentDoc> search(String keyword, Long spiderId,
                                          int current, int size) {
        PageRequest pageRequest = PageRequest.of(current - 1, size);

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword;
            boolBuilder.must(m -> m.match(mt -> mt.field("title").query(kw)));
            boolBuilder.should(s -> s.match(mt -> mt.field("content").query(kw)));
            boolBuilder.minimumShouldMatch("0");
        } else {
            boolBuilder.must(m -> m.matchAll(mt -> mt));
        }

        if (spiderId != null) {
            boolBuilder.must(m -> m.term(t -> t.field("spiderId").value(spiderId)));
        }

        Query query = Query.of(q -> q.bool(boolBuilder.build()));

        SearchRequest request = new SearchRequest.Builder()
                .index("spider_content")
                .from(pageRequest.getPageNumber() * pageRequest.getPageSize())
                .size(pageRequest.getPageSize())
                .query(query)
                .build();

        try {
            SearchResponse<SpiderContentDoc> response = elasticsearchClient
                    .search(request, SpiderContentDoc.class);
            List<SpiderContentDoc> docs = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageImpl<>(docs, pageRequest, total);
        } catch (Exception e) {
            log.error("ES search failed", e);
            return new PageImpl<>(List.of(), pageRequest, 0);
        }
    }

    public SpiderContentDoc getById(String id) throws java.io.IOException {
        return elasticsearchClient
                .get(g -> g.index("spider_content").id(id), SpiderContentDoc.class)
                .source();
    }
}
