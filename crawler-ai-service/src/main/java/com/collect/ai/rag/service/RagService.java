package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.Spider;
import com.collect.ai.mapper.SpiderMapper;
import com.collect.ai.rag.config.RagProperties;
import com.collect.ai.rag.dto.RagDtos;
import com.collect.common.security.LoginUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final SpiderMapper spiderMapper;

    @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.username:}")
    private String elasticsearchUsername;

    @Value("${spring.elasticsearch.password:}")
    private String elasticsearchPassword;

    public RagDtos.SyncResult syncContentIndex() {
        ensureEnabled();
        RagProperties.Rag rag = properties.getRag();
        List<JsonNode> documents = fetchContentDocuments();
        int chunkSize = Math.max(200, rag.getChunkSize());
        int overlap = Math.max(0, Math.min(rag.getChunkOverlap(), chunkSize - 1));
        deleteExistingVectors();

        List<ObjectNode> operations = new ArrayList<>();
        long chunks = 0;
        for (JsonNode document : documents) {
            String id = document.path("_id").asText();
            JsonNode source = document.path("_source");
            String title = source.path("title").asText("");
            String content = source.path("content").asText("");
            String url = source.path("url").asText("");
            String spiderName = source.path("spiderName").asText("");
            long spiderId = source.path("spiderId").asLong(-1);
            List<String> parts = splitText(title + "\n" + content, chunkSize, overlap);
            for (int i = 0; i < parts.size(); i++) {
                String chunkId = id + ":" + i;
                List<Double> embedding = createEmbedding(parts.get(i));
                ObjectNode action = objectMapper.createObjectNode();
                action.set("index", objectMapper.createObjectNode()
                        .put("_index", properties.resolveVectorIndex())
                        .put("_id", chunkId));
                operations.add(action);

                ObjectNode row = objectMapper.createObjectNode();
                row.put("doc_id", id);
                row.put("chunk_id", chunkId);
                row.put("title", title);
                row.put("content", parts.get(i));
                row.put("url", url);
                row.put("spiderName", spiderName);
                row.put("embedding_model", rag.getEmbeddingModel());
                if (spiderId >= 0) row.put("spiderId", spiderId);
                ArrayNode vector = row.putArray(properties.resolveVectorField());
                embedding.forEach(vector::add);
                operations.add(row);
                chunks++;
                if (operations.size() >= 200) {
                    bulkOperations(operations);
                    operations.clear();
                }
            }
        }

        if (!operations.isEmpty()) {
            bulkOperations(operations);
        }

        RagDtos.SyncResult result = new RagDtos.SyncResult();
        result.setSourceDocuments(documents.size());
        result.setChunks(chunks);
        result.setVectorDocuments(chunks);
        return result;
    }

    public RagDtos.QueryResponse query(RagDtos.QueryRequest request) {
        return query(request, isLoggedIn(), List.of(), null);
    }

    public RagDtos.QueryResponse queryForLoggedInUser(
            RagDtos.QueryRequest request, List<RagDtos.ChatHistoryMessage> history) {
        return query(request, true, history, null);
    }

    public RagDtos.QueryResponse queryForLoggedInUser(
            RagDtos.QueryRequest request, Consumer<String> tokenConsumer) {
        return query(request, true, List.of(), tokenConsumer);
    }

    public RagDtos.QueryResponse queryForLoggedInUser(
            RagDtos.QueryRequest request,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer) {
        return query(request, true, history, tokenConsumer);
    }

    private RagDtos.QueryResponse query(
            RagDtos.QueryRequest request,
            boolean loggedIn,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer) {
        ensureEnabled();
        if (request == null || request.getQuestion() == null
                || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("question不能为空");
        }
        RagProperties.Rag rag = properties.getRag();
        int topK = request.getTopK() == null
                ? rag.getTopK()
                : Math.max(1, Math.min(request.getTopK(), 20));

        List<Double> queryVector = createEmbedding(request.getQuestion());
        ObjectNode script = objectMapper.createObjectNode();
        script.put("source",
                "cosineSimilarity(params.query_vector, '" + properties.resolveVectorField()
                        + "') + 1.0");
        script.set("params", objectMapper.createObjectNode()
                .set("query_vector", objectMapper.valueToTree(queryVector)));

        ObjectNode filter = null;
        if (!loggedIn) {
            List<Long> publicSpiderIds = spiderMapper.selectList(
                            new LambdaQueryWrapper<Spider>().eq(Spider::getIsPublic, 1))
                    .stream().map(Spider::getId).toList();
            if (publicSpiderIds.isEmpty()) {
                return emptyQueryResponse(request.getQuestion(), rag, history, tokenConsumer);
            }
            if (request.getSpiderId() != null
                    && !publicSpiderIds.contains(request.getSpiderId())) {
                return emptyQueryResponse(request.getQuestion(), rag, history, tokenConsumer);
            }
            ArrayNode publicValues = objectMapper.createArrayNode();
            publicSpiderIds.forEach(publicValues::add);
            filter = objectMapper.createObjectNode();
            filter.set("terms", objectMapper.createObjectNode().set("spiderId", publicValues));
        }
        if (request.getSpiderId() != null) {
            ObjectNode spiderFilter = objectMapper.createObjectNode();
            spiderFilter.putObject("term").put("spiderId", request.getSpiderId());
            filter = combineFilters(filter, spiderFilter);
        } else if (request.getSpiderGroup() != null && !request.getSpiderGroup().isBlank()) {
            List<Long> spiderIds = spiderMapper.selectList(
                            new LambdaQueryWrapper<Spider>()
                                    .eq(Spider::getGroup, request.getSpiderGroup()))
                    .stream().map(Spider::getId).toList();
            if (spiderIds.isEmpty()) {
                return emptyQueryResponse(request.getQuestion(), rag, history, tokenConsumer);
            }
            ArrayNode values = objectMapper.createArrayNode();
            spiderIds.forEach(values::add);
            ObjectNode groupFilter = objectMapper.createObjectNode();
            groupFilter.set("terms", objectMapper.createObjectNode().set("spiderId", values));
            filter = combineFilters(filter, groupFilter);
        }

        ObjectNode scriptScore = objectMapper.createObjectNode();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", topK);
        body.set("_source", objectMapper.valueToTree(List.of(
                "doc_id", "chunk_id", "title", "content", "url", "spiderName")));
        if (filter != null) {
            ObjectNode boolQuery = objectMapper.createObjectNode();
            boolQuery.set("filter", objectMapper.createArrayNode().add(filter));
            ObjectNode filteredQuery = objectMapper.createObjectNode();
            filteredQuery.set("bool", boolQuery);
            scriptScore.set("query", filteredQuery);
        } else {
            scriptScore.set("query", objectMapper.createObjectNode()
                    .set("match_all", objectMapper.createObjectNode()));
        }
        scriptScore.set("script", script);
        ObjectNode query = objectMapper.createObjectNode();
        query.set("script_score", scriptScore);
        body.set("query", query);

        JsonNode root;
        try {
            HttpResponse<String> search = elasticsearchRequest("POST",
                    "/" + properties.resolveVectorIndex() + "/_search", toJson(body));
            if (search.statusCode() >= 300) {
                throw new IllegalStateException("RAG 向量检索失败: " + search.statusCode()
                        + " " + search.body());
            }
            root = objectMapper.readTree(search.body());
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("RAG 向量检索失败: " + e.getMessage(), e);
        }

        List<RagDtos.SourceReference> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (JsonNode hit : root.path("hits").path("hits")) {
            JsonNode source = hit.path("_source");
            RagDtos.SourceReference ref = new RagDtos.SourceReference();
            ref.setId(source.path("doc_id").asText());
            ref.setTitle(source.path("title").asText(""));
            ref.setUrl(source.path("url").asText(""));
            ref.setSpiderName(source.path("spiderName").asText(""));
            ref.setScore(hit.path("_score").asDouble(0));
            String text = source.path("content").asText("");
            ref.setSnippet(text.length() <= 500 ? text : text.substring(0, 500));
            sources.add(ref);
            context.append("标题：").append(ref.getTitle()).append("\n")
                    .append("来源：").append(ref.getUrl()).append("\n")
                    .append("内容：").append(text).append("\n\n");
        }

        String answer = tokenConsumer == null
                ? createChatAnswer(request.getQuestion(), context.toString(), history)
                : createStreamingChatAnswer(
                        request.getQuestion(), context.toString(), history, tokenConsumer);
        RagDtos.QueryResponse response = new RagDtos.QueryResponse();
        response.setAnswer(answer);
        response.setSources(sources);
        response.setModel(rag.getChatModel());
        response.setVectorIndex(properties.resolveVectorIndex());
        response.setVectorField(properties.resolveVectorField());
        return response;
    }

    private List<JsonNode> fetchContentDocuments() throws IllegalStateException {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("size", 1000);
            body.set("sort", objectMapper.createArrayNode().add("_doc"));
            body.set("query", objectMapper.createObjectNode()
                    .set("match_all", objectMapper.createObjectNode()));
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + properties.getRag().getSourceIndex() + "/_search?scroll=1m", toJson(body));
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取 ES 源文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<JsonNode> documents = new ArrayList<>();
            root.path("hits").path("hits").forEach(documents::add);
            String scrollId = root.path("_scroll_id").asText("");
            while (!scrollId.isBlank()) {
                ObjectNode scrollBody = objectMapper.createObjectNode();
                scrollBody.put("scroll", "1m");
                scrollBody.put("scroll_id", scrollId);
                HttpResponse<String> next = elasticsearchRequest("POST",
                        "/_search/scroll", toJson(scrollBody));
                if (next.statusCode() >= 300) break;
                root = objectMapper.readTree(next.body());
                List<JsonNode> nextHits = new ArrayList<>();
                root.path("hits").path("hits").forEach(nextHits::add);
                if (nextHits.isEmpty()) break;
                documents.addAll(nextHits);
                scrollId = root.path("_scroll_id").asText("");
            }
            if (!scrollId.isBlank()) {
                ObjectNode clearBody = objectMapper.createObjectNode();
                clearBody.putArray("scroll_id").add(scrollId);
                elasticsearchRequest("DELETE", "/_search/scroll", toJson(clearBody));
            }
            return documents;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取 ES 源文档失败: " + e.getMessage(), e);
        }
    }

    private List<Double> createEmbedding(String text) {
        RagProperties.Rag rag = properties.getRag();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rag.getEmbeddingModel());
            body.put("ollama".equalsIgnoreCase(rag.getEmbeddingProvider()) ? "prompt" : "input", text);
            String path = "ollama".equalsIgnoreCase(rag.getEmbeddingProvider())
                    ? "/api/embeddings"
                    : "/embeddings";
            HttpResponse<String> response = openAiRequest(path, body);
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("调用 Embedding 模型失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vector;
            if (root.path("embedding").isArray()) {
                vector = root.path("embedding");
            } else if (root.path("embeddings").isArray()
                    && root.path("embeddings").size() > 0
                    && root.path("embeddings").get(0).isArray()) {
                vector = root.path("embeddings").get(0);
            } else if (root.path("data").isArray()
                    && root.path("data").size() > 0) {
                vector = root.path("data").get(0).path("embedding");
            } else {
                vector = objectMapper.createArrayNode();
            }
            List<Double> result = new ArrayList<>();
            if (vector.isArray()) {
                for (JsonNode value : vector) result.add(value.asDouble());
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("Embedding 模型未返回向量");
            }
            if (result.size() != properties.resolveDimensions()) {
                throw new IllegalStateException("Embedding 向量维度不匹配: 实际 "
                        + result.size() + "，配置 " + properties.resolveDimensions());
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用 Embedding 模型失败: " + e.getMessage(), e);
        }
    }

    private String createChatAnswer(
            String question, String context, List<RagDtos.ChatHistoryMessage> history) {
        RagProperties.Rag rag = properties.getRag();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rag.getChatModel());
            ArrayNode messages = body.putArray("messages");
            appendChatMessages(messages, question, context, history);
            if (!"ollama".equalsIgnoreCase(rag.getChatProvider())) {
                body.put("temperature", 0.2);
            } else {
                body.put("stream", false);
                body.putObject("options").put("num_ctx", rag.getContextLength());
            }
            String path = "ollama".equalsIgnoreCase(rag.getChatProvider())
                    ? "/api/chat"
                    : "/chat/completions";
            HttpResponse<String> response = openAiRequest(path, body);
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("调用聊天模型失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if ("ollama".equalsIgnoreCase(rag.getChatProvider())) {
                return root.path("message").path("content").asText("");
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("聊天模型未返回 choices");
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用聊天模型失败: " + e.getMessage(), e);
        }
    }

    private String createStreamingChatAnswer(
            String question,
            String context,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer) {
        RagProperties.Rag rag = properties.getRag();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rag.getChatModel());
            ArrayNode messages = body.putArray("messages");
            appendChatMessages(messages, question, context, history);
            String provider = rag.getChatProvider();
            String path = "ollama".equalsIgnoreCase(provider)
                    ? "/api/chat"
                    : "/chat/completions";
            body.put("stream", true);
            if (!"ollama".equalsIgnoreCase(provider)) {
                body.put("temperature", 0.2);
            } else {
                body.putObject("options").put("num_ctx", rag.getContextLength());
            }
            streamChatRequest(path, body, tokenConsumer);
            return "";
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用聊天模型失败: " + e.getMessage(), e);
        }
    }

    private void appendChatMessages(
            ArrayNode messages,
            String question,
            String context,
            List<RagDtos.ChatHistoryMessage> history) {
        boolean hasContext = context != null && !context.isBlank();
        messages.addObject()
                .put("role", "system")
                .put("content", hasContext
                        ? "你是一个只依据提供资料回答问题的中文助手。"
                                + "如果资料不足，请明确说明不能确定，不要编造。回答时可引用资料标题或来源。"
                        : "你是一个友好的中文助手，请结合当前对话历史直接、准确地回答用户问题。");
        if (history != null) {
            for (RagDtos.ChatHistoryMessage item : history) {
                if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                if (!"user".equals(item.getRole()) && !"assistant".equals(item.getRole())) {
                    continue;
                }
                messages.addObject()
                        .put("role", item.getRole())
                        .put("content", item.getContent());
            }
        }
        String currentMessage = hasContext
                ? "资料：\n" + context + "\n\n问题：" + question
                : question;
        messages.addObject().put("role", "user").put("content", currentMessage);
    }

            private void streamChatRequest(
                    String path, ObjectNode body, Consumer<String> tokenConsumer) throws Exception {
                RagProperties.Rag rag = properties.getRag();
                String baseUrl = path.equals("/api/chat")
                        ? rag.getChatBaseUrl().replaceAll("/$", "").replaceAll("/v1$", "")
                        : rag.getChatBaseUrl().replaceAll("/$", "");
                URI uri = URI.create(baseUrl + path);
                String requestBody = objectMapper.writeValueAsString(body);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json");
                String apiKey = rag.getApiKey();
                boolean hasApiKey = apiKey != null && !apiKey.isBlank();
                if (hasApiKey) {
                    builder.header("Authorization", "Bearer " + apiKey);
                }
                HttpRequest request = builder.POST(HttpRequest.BodyPublishers
                        .ofString(requestBody, StandardCharsets.UTF_8)).build();
                long startNanos = System.nanoTime();
                String logUri = safeLogUri(uri);
                log.info("发送流式聊天请求: method=POST, url={}, provider={}, model={}, bodyBytes={}, apiKeyConfigured={}",
                        logUri, rag.getChatProvider(), safeLogValue(rag.getChatModel(), 128),
                        requestBody.getBytes(StandardCharsets.UTF_8).length, hasApiKey);

                try {
                    HttpResponse<java.io.InputStream> response = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10)).build()
                            .send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() >= 300) {
                        try (java.io.InputStream input = response.body()) {
                            String errorBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                            log.warn("流式聊天请求返回错误: url={}, status={}, responseBytes={}, elapsedMs={}",
                                    logUri, response.statusCode(),
                                    errorBody.getBytes(StandardCharsets.UTF_8).length,
                                    Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
                            throw new IllegalStateException("调用聊天模型失败: " + response.statusCode()
                                    + " " + errorBody);
                        }
                    }
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                        if (path.equals("/api/chat")) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.isBlank()) emitOllamaToken(line, tokenConsumer);
                            }
                        } else {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    emitOpenAiToken(line.substring(5).trim(), tokenConsumer);
                                }
                            }
                        }
                    }
                    log.info("流式聊天请求完成: url={}, provider={}, model={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), safeLogValue(rag.getChatModel(), 128),
                            Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
                } catch (Exception e) {
                    log.warn("流式聊天请求失败: url={}, provider={}, model={}, exception={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), safeLogValue(rag.getChatModel(), 128),
                            e.getClass().getSimpleName(),
                            Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
                    throw e;
                }
            }

            private void emitOllamaToken(String line, Consumer<String> tokenConsumer) throws Exception {
                JsonNode root = objectMapper.readTree(line);
                String token = root.path("message").path("content").asText("");
                if (!token.isEmpty()) tokenConsumer.accept(token);
                if (root.path("error").isTextual()) {
                    throw new IllegalStateException("聊天模型返回错误: " + root.path("error").asText());
                }
            }

            private void emitOpenAiToken(String data, Consumer<String> tokenConsumer) throws Exception {
                if (data.isEmpty() || "[DONE]".equals(data)) return;
                JsonNode root = objectMapper.readTree(data);
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.isEmpty()) return;
                String token = choices.get(0).path("delta").path("content").asText("");
                if (!token.isEmpty()) tokenConsumer.accept(token);
            }

    private HttpResponse<String> openAiRequest(String path, ObjectNode body) throws Exception {
        RagProperties.Rag rag = properties.getRag();
        String baseUrl;
        if (path.equals("/api/embeddings")) {
            baseUrl = rag.getEmbeddingBaseUrl().replaceAll("/$", "").replaceAll("/v1$", "");
        } else if (path.equals("/api/chat")) {
            baseUrl = rag.getChatProvider().equalsIgnoreCase("ollama")
                    ? rag.getChatBaseUrl().replaceAll("/$", "").replaceAll("/v1$", "")
                    : rag.getChatBaseUrl().replaceAll("/$", "");
        } else if (path.startsWith("/chat/completions")) {
            baseUrl = rag.getChatBaseUrl().replaceAll("/$", "");
        } else if (path.equals("/embeddings")) {
            baseUrl = rag.getEmbeddingBaseUrl().replaceAll("/$", "");
        } else {
            throw new IllegalArgumentException("不支持的模型 API 路径: " + path);
        }
        URI requestUri = URI.create(baseUrl + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(requestUri)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json");
        boolean embeddingRequest = path.equals("/api/embeddings") || path.equals("/embeddings");
        String apiKey = embeddingRequest ? rag.getEmbeddingApiKey() : rag.getApiKey();
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();
        if (hasApiKey) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        String requestBody = objectMapper.writeValueAsString(body);
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers
                        .ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        boolean chatRequest = path.equals("/api/chat") || path.startsWith("/chat/completions");
        long startNanos = System.nanoTime();
        String logUri = chatRequest ? safeLogUri(requestUri) : null;
        String chatModel = chatRequest ? safeLogValue(rag.getChatModel(), 128) : null;
        if (chatRequest) {
            log.info("发送聊天模型请求: method=POST, url={}, provider={}, model={}, contentType=application/json, "
                            + "bodyBytes={}, messages={}, apiKeyConfigured={}",
                    logUri, rag.getChatProvider(), chatModel,
                    requestBody.getBytes(StandardCharsets.UTF_8).length,
                    summarizeMessages(body.path("messages")), hasApiKey);
        }
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (chatRequest) {
                long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                String contentType = response.headers().firstValue("content-type").orElse("unknown");
                if (response.statusCode() >= 300) {
                    log.warn("聊天模型请求返回错误: url={}, provider={}, model={}, status={}, "
                                    + "contentType={}, responseBytes={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), chatModel, response.statusCode(),
                            contentType, response.body().getBytes(StandardCharsets.UTF_8).length, elapsedMs);
                } else {
                    log.info("聊天模型请求完成: url={}, provider={}, model={}, status={}, "
                                    + "contentType={}, responseBytes={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), chatModel, response.statusCode(),
                            contentType, response.body().getBytes(StandardCharsets.UTF_8).length, elapsedMs);
                }
            }
            return response;
        } catch (Exception e) {
            if (chatRequest) {
                long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                log.warn("聊天模型请求未收到响应: url={}, provider={}, model={}, exception={}, "
                                + "elapsedMs={}",
                        logUri, rag.getChatProvider(), chatModel,
                        e.getClass().getSimpleName(), elapsedMs);
            }
            throw e;
        }
    }

    private static String safeLogUri(URI uri) throws URISyntaxException {
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                uri.getPath(), null, null).toString();
    }

    private static String safeLogValue(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}]", "?");
        return sanitized.length() <= maxLength
                ? sanitized : sanitized.substring(0, maxLength);
    }

    private static List<String> summarizeMessages(JsonNode messages) {
        List<String> summary = new ArrayList<>();
        if (!messages.isArray()) {
            return summary;
        }
        for (JsonNode message : messages) {
            summary.add(message.path("role").asText("unknown") + ":"
                    + message.path("content").asText("").length() + "chars");
        }
        return summary;
    }

    private HttpResponse<String> elasticsearchRequest(String method, String path, String body)
            throws Exception {
        String uri = elasticsearchUris.split(",")[0].trim();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");
        if (!elasticsearchUsername.isBlank()) {
            String auth = elasticsearchUsername + ":" + elasticsearchPassword;
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }
        HttpRequest request = (body == null
                ? builder.method(method, HttpRequest.BodyPublishers.noBody())
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)))
                .build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void deleteExistingVectors() {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("query", objectMapper.createObjectNode()
                .set("term", objectMapper.createObjectNode()
                        .put("embedding_model", properties.getRag().getEmbeddingModel())));
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + properties.resolveVectorIndex() + "/_delete_by_query?refresh=true&conflicts=proceed",
                    toJson(body));
            if (response.statusCode() >= 300 && response.statusCode() != 404) {
                throw new IllegalStateException("清理旧 RAG 向量失败: " + response.statusCode()
                        + " " + response.body());
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("清理旧 RAG 向量失败: " + e.getMessage(), e);
        }
    }

    private void bulkOperations(List<ObjectNode> operations) {
        StringBuilder bulk = new StringBuilder();
        for (ObjectNode operation : operations) {
            bulk.append(toJson(operation)).append('\n');
        }
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/_bulk?refresh=true", bulk.toString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("写入 RAG 向量索引失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode result = objectMapper.readTree(response.body());
            if (result.path("errors").asBoolean(false)) {
                String firstError = "ES _bulk 未返回具体错误";
                JsonNode items = result.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        if (item.path("index").path("error").isObject()) {
                            firstError = item.path("index").path("error").toString();
                            break;
                        }
                    }
                }
                throw new IllegalStateException("写入 RAG 向量索引失败: " + firstError);
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("写入 RAG 向量索引失败: " + e.getMessage(), e);
        }
    }

    private boolean isLoggedIn() {
        try {
            LoginUtils.getLoginUser();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private ObjectNode combineFilters(ObjectNode existing, ObjectNode additional) {
        if (existing == null) return additional;
        if (existing.path("bool").isObject()) {
            ObjectNode bool = (ObjectNode) existing.get("bool");
            ArrayNode filters = bool.path("filter").isArray()
                    ? (ArrayNode) bool.get("filter")
                    : bool.putArray("filter");
            filters.add(additional);
            return existing;
        }
        ObjectNode bool = objectMapper.createObjectNode();
        ArrayNode filters = bool.putArray("filter");
        filters.add(existing);
        filters.add(additional);
        return objectMapper.createObjectNode().set("bool", bool);
    }

    private void ensureEnabled() {
        if (!properties.getRag().isEnabled()) {
            throw new IllegalStateException("RAG 未启用，请先通过 AI 配置接口启用");
        }
    }

    private RagDtos.QueryResponse emptyQueryResponse(
            String question,
            RagProperties.Rag rag,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer) {
        RagDtos.QueryResponse response = new RagDtos.QueryResponse();
        response.setAnswer(tokenConsumer == null
                ? createChatAnswer(question, "", history)
                : createStreamingChatAnswer(question, "", history, tokenConsumer));
        response.setSources(List.of());
        response.setModel(rag.getChatModel());
        response.setVectorIndex(properties.resolveVectorIndex());
        response.setVectorField(properties.resolveVectorField());
        return response;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 RAG 请求失败: " + e.getMessage(), e);
        }
    }

    private static List<String> splitText(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end).trim());
            if (end == text.length()) break;
            start = end - overlap;
        }
        return chunks;
    }
}
