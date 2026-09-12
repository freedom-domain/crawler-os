package com.collect.search.es;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SpiderContentRepository extends ElasticsearchRepository<SpiderContentDoc, String> {

    @Query("SELECT * FROM SpiderContentDoc WHERE " +
            "(:keyword IS NULL OR title LIKE CONCAT('%', :keyword, '%') OR content LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:spiderId IS NULL OR spiderId = :spiderId)")
    Page<SpiderContentDoc> search(String keyword, Long spiderId, Pageable pageable);
}
