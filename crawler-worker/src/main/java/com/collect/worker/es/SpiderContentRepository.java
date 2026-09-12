package com.collect.worker.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Optional;

public interface SpiderContentRepository extends ElasticsearchRepository<SpiderContentDoc, String> {

    Optional<SpiderContentDoc> findByUrl(String url);
}
