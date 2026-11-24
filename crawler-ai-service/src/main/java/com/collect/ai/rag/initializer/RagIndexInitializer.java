package com.collect.ai.rag.initializer;

import com.collect.ai.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * RAG 向量索引初始化器。
 *
 * <p>应用启动（重启）时不创建向量索引、不向其中写入任何数据；
 * 索引仅在需要时按需初始化：更新 RAG 配置（{@link com.collect.ai.rag.service.RagConfigService}）
 * 或知识库向量化首次写入前（{@link com.collect.ai.rag.service.RagService}）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexInitializer {

    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.username:}")
    private String elasticsearchUsername;

    @Value("${spring.elasticsearch.password:}")
    private String elasticsearchPassword;

    public void ensureVectorIndex() {
        String index = properties.resolveVectorIndex();
        try {
            HttpResponse<String> existing = elasticsearchRequest("HEAD", "/" + index, null);
            if (existing.statusCode() == 404) {
                String vectorField = properties.resolveVectorField();
                int dimensions = properties.resolveDimensions();
                String body = """
                        {"settings":{"number_of_shards":1},"mappings":{"properties":{
                        "doc_id":{"type":"keyword"},"chunk_id":{"type":"keyword"},"title":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                        "heading":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                        "content":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                        "parent_text":{"type":"text","index":false},"url":{"type":"keyword"},"spiderId":{"type":"long"},
                        "spiderName":{"type":"keyword"},"knowledgeBaseId":{"type":"long"},"embedding_model":{"type":"keyword"},"%s":{
                        "type":"dense_vector","dims":%d}}}}
                        """.formatted(vectorField, dimensions);
                HttpResponse<String> created = elasticsearchRequest("PUT", "/" + index, body);
                if (created.statusCode() >= 200 && created.statusCode() < 300) {
                    log.info("已创建 RAG 向量索引: {}，模型字段: {}, dimensions: {}",
                            index, vectorField, dimensions);
                } else {
                    throw new IllegalStateException("创建 RAG 向量索引失败: "
                            + created.statusCode() + " " + created.body());
                }
            } else {
                if (existing.statusCode() < 200 || existing.statusCode() >= 300) {
                    throw new IllegalStateException("检查 RAG 向量索引失败: "
                            + existing.statusCode() + " " + existing.body());
                }
                validateVectorMapping(index);
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("初始化 RAG 向量索引失败: " + e.getMessage(), e);
        }
    }

    public void deleteVectorIndex(String index) {
        try {
            HttpResponse<String> response = elasticsearchRequest("DELETE", "/" + index, null);
            if (response.statusCode() != 404
                    && (response.statusCode() < 200 || response.statusCode() >= 300)) {
                throw new IllegalStateException("删除旧 RAG 向量索引失败: "
                        + response.statusCode() + " " + response.body());
            }
            log.info("已删除旧 RAG 向量索引: {}", index);
        } catch (Exception e) {
            log.error("删除旧 RAG 向量索引失败: {}", index, e);
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("删除旧 RAG 向量索引失败: " + index, e);
        }
    }

    private void validateVectorMapping(String index) throws Exception {
        String vectorField = properties.resolveVectorField();
        int dimensions = properties.resolveDimensions();
        HttpResponse<String> mapping = elasticsearchRequest("GET", "/" + index + "/_mapping", null);
        if (mapping.statusCode() >= 300) {
            log.warn("读取 RAG 向量索引 mapping 失败: {} {}", mapping.statusCode(), mapping.body());
            return;
        }
        JsonNode root = objectMapper.readTree(mapping.body());
        JsonNode indexNode = root.path(index);
        JsonNode mappingNode = indexNode.isMissingNode()
                ? root.path("mappings")
                : indexNode.path("mappings");
        JsonNode field = mappingNode.path("properties").path(vectorField);
        if (field.isMissingNode()) {
            String body = "{\"properties\":{\"%s\":{\"type\":\"dense_vector\",\"dims\":%d}}}"
                    .formatted(vectorField, dimensions);
            HttpResponse<String> updated = elasticsearchRequest("PUT",
                    "/" + index + "/_mapping", body);
            if (updated.statusCode() >= 200 && updated.statusCode() < 300) {
                log.info("已为 RAG 向量索引补充字段 {}.{}", index, vectorField);
            } else {
                log.warn("补充 RAG 向量字段失败: {} {}", updated.statusCode(), updated.body());
            }
            return;
        }
        int actualDimensions = field.path("dims").asInt(-1);
        if (actualDimensions != dimensions) {
            throw new IllegalStateException("RAG 向量索引维度不匹配: 索引 "
                    + index + " 字段 " + vectorField + " 为 " + actualDimensions
                    + "，当前模型要求 " + dimensions);
        }
        JsonNode properties = mappingNode.path("properties");
        if (properties.path("title").isMissingNode()
                || properties.path("content").isMissingNode()) {
            String textMapping = """
                    {"properties":{
                    "title":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                    "content":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"}}}
                    """;
            HttpResponse<String> updated = elasticsearchRequest("PUT",
                    "/" + index + "/_mapping", textMapping);
            if (updated.statusCode() >= 200 && updated.statusCode() < 300) {
                log.info("已为 RAG 向量索引补充中文分词字段: {}.title, {}.content", index, index);
            } else {
                log.warn("补充中文分词字段失败: {} {}", updated.statusCode(), updated.body());
            }
        }
        log.info("RAG 向量索引配置有效: {}.{} = {}维", index, vectorField, actualDimensions);
    }

    private HttpResponse<String> elasticsearchRequest(String method, String path, String body)
            throws Exception {
        String uri = elasticsearchUris.split(",")[0].trim();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri + path))
                .timeout(Duration.ofSeconds(30));
        if (!elasticsearchUsername.isBlank()) {
            String auth = elasticsearchUsername + ":" + elasticsearchPassword;
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }
        builder.header("Content-Type", "application/json");
        HttpRequest request = (body == null
                ? builder.method(method, HttpRequest.BodyPublishers.noBody())
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)))
                .build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
