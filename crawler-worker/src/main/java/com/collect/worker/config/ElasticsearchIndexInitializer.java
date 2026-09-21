package com.collect.worker.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.collect.worker.es.SpiderContentDoc;
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
    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndex() {
        try {
            var ops = elasticsearchOperations.indexOps(IndexCoordinates.of(contentIndex));
            if (ops.exists()) {
                Map<String, Object> mappings = ops.getMapping();
                Object crawlTimeType = extractFieldType(mappings, "crawlTime");
                if (crawlTimeType != null && !"date".equals(crawlTimeType)) {
                    log.warn("索引 {} 的 crawlTime 字段类型为 {}，需要删除重建", contentIndex, crawlTimeType);
                    ops.delete();
                    ops.create();
                    ops.putMapping(ops.createMapping(SpiderContentDoc.class));
                    log.info("已重建 ES 索引: {}", contentIndex);
                    return;
                }
                // 补充新增字段到已有索引的 mapping
                ensureField(mappings, "tags", "keyword");
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

    /**
     * 若索引 mapping 中缺少指定字段，则动态添加。
     */
    private void ensureField(Map<String, Object> mappings, String field, String type) {
        if (extractFieldType(mappings, field) == null) {
            try {
                elasticsearchClient.indices().putMapping(m -> m
                        .index(contentIndex)
                        .properties(field, p -> p.keyword(k -> k))
                );
                log.info("已为索引 {} 添加字段: {} ({})", contentIndex, field, type);
            } catch (Exception e) {
                log.warn("添加字段 {} 失败: {}", field, e.getMessage());
            }
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
