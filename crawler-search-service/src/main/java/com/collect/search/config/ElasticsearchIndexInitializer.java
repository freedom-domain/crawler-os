package com.collect.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.collect.search.es.SpiderContentDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndex() {
        try {
            var ops = elasticsearchOperations.indexOps(IndexCoordinates.of(contentIndex));
            if (ops.exists()) {
                Map<String, Object> mappings = ops.getMapping();
                String invalidDateField = findInvalidDateField(mappings);
                if (invalidDateField != null) {
                    log.warn("索引 {} 的 {} 字段不是 date 类型，需要删除重建", contentIndex, invalidDateField);
                    ops.delete();
                    ops.create();
                    ops.putMapping(ops.createMapping(SpiderContentDoc.class));
                    log.info("已重建 ES 索引: {}", contentIndex);
                    return;
                }
                // 补充新增字段到已有索引的 mapping
                ensureField(mappings, "spiderGroup");
                ensureField(mappings, "tags");
                ensureDateField(mappings, "updateTime");
            } else {
                ops.create();
                ops.putMapping(ops.createMapping(SpiderContentDoc.class));
                log.info("已创建 ES 索引: {}", contentIndex);
            }
            // 去掉 ES 默认 10000 条最大返回限制
            ensureMaxResultWindow();
        } catch (Exception e) {
            log.error("初始化 ES 索引失败: {}", contentIndex, e);
        }
    }

    private void ensureMaxResultWindow() {
        try {
            elasticsearchClient.indices().putSettings(s -> s
                    .index(contentIndex)
                    .settings(set -> set
                            .maxResultWindow(Integer.MAX_VALUE)
                    )
            );
            log.info("已设置索引 {} 的 max_result_window 为 Integer.MAX_VALUE", contentIndex);
        } catch (Exception e) {
            log.warn("设置索引 {} 的 max_result_window 失败: {}", contentIndex, e.getMessage());
        }
    }

    private void ensureField(Map<String, Object> mappings, String field) {
        if (extractFieldType(mappings, field) == null) {
            try {
                elasticsearchClient.indices().putMapping(m -> m
                        .index(contentIndex)
                        .properties(field, p -> p.keyword(k -> k))
                );
                log.info("已为索引 {} 添加字段: {}", contentIndex, field);
            } catch (Exception e) {
                log.warn("添加字段 {} 失败: {}", field, e.getMessage());
            }
        }
    }

    private void ensureDateField(Map<String, Object> mappings, String field) {
        if (extractFieldType(mappings, field) == null) {
            try {
                elasticsearchClient.indices().putMapping(m -> m
                        .index(contentIndex)
                        .properties(field, p -> p.date(d -> d))
                );
                log.info("已为索引 {} 添加日期字段: {}", contentIndex, field);
            } catch (Exception e) {
                log.warn("添加日期字段 {} 失败: {}", field, e.getMessage());
            }
        }
    }

    private String findInvalidDateField(Map<String, Object> mappings) {
        for (String field : List.of("crawlTime", "updateTime")) {
            String type = extractFieldType(mappings, field);
            if (type != null && !"date".equals(type)) {
                return field;
            }
        }
        return null;
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
