package com.collect.search.config;

import com.collect.search.es.SpiderContentDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndex() {
        try {
            var ops = elasticsearchOperations.indexOps(IndexCoordinates.of(contentIndex));
            if (!ops.exists()) {
                ops.create();
                ops.putMapping(ops.createMapping(SpiderContentDoc.class));
                log.info("已创建 ES 索引: {}", contentIndex);
            }
        } catch (Exception e) {
            log.error("初始化 ES 索引失败: {}", contentIndex, e);
        }
    }
}
