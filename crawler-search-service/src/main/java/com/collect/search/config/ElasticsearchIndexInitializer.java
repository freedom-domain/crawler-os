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

import java.util.Map;

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
            if (ops.exists()) {
                Map<String, Object> mappings = ops.getMapping();
                String crawlTimeType = extractFieldType(mappings, "crawlTime");
                if (crawlTimeType != null && !"date".equals(crawlTimeType)) {
                    log.warn("索引 {} 的 crawlTime 字段类型为 {}，需要删除重建", contentIndex, crawlTimeType);
                    ops.delete();
                    ops.create();
                    ops.putMapping(ops.createMapping(SpiderContentDoc.class));
                    log.info("已重建 ES 索引: {}", contentIndex);
                    return;
                }
            } else {
                ops.create();
                ops.putMapping(ops.createMapping(SpiderContentDoc.class));
                log.info("已创建 ES 索引: {}", contentIndex);
            }
        } catch (Exception e) {
            log.error("初始化 ES 索引失败: {}", contentIndex, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractFieldType(Map<String, Object> mappings, String field) {
        if (mappings == null) return null;
        Object props = mappings.get("properties");
        if (props instanceof Map) {
            Object fieldDef = ((Map<String, Object>) props).get(field);
            if (fieldDef instanceof Map) {
                return (String) ((Map<String, Object>) fieldDef).get("type");
            }
        }
        Object fieldDef = mappings.get(field);
        if (fieldDef instanceof Map) {
            return (String) ((Map<String, Object>) fieldDef).get("type");
        }
        return null;
    }
}
