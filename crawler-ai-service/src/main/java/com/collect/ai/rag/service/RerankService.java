package com.collect.ai.rag.service;

import com.collect.ai.entity.AiRagRerankModel;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RerankService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Result(int index, double score) {
    }

    private final ObjectMapper objectMapper;

    /**
     * 重排全部候选文档，仅返回相关度最高的前 topN 条（重排 API 的 top_n/top_k）。
     * topN <= 0 时返回全部候选。
     */
    public List<Result> rerank(AiRagRerankModel model, String query, List<String> documents, int topN) {
        if (model == null || !StringUtils.hasText(model.getRerankBaseUrl())
                || !StringUtils.hasText(model.getRerankModel())) {
            throw new BizException("重排模型配置不完整");
        }
        int limit = topN <= 0 ? documents.size()
                : Math.max(1, Math.min(topN, documents.size()));
        String provider = model.getRerankProvider() == null
                ? "" : model.getRerankProvider().trim().toLowerCase(Locale.ROOT);
        URI endpoint = null;
        long startTime = System.nanoTime();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            if ("dashscope".equals(provider)) {
                body.put("model", model.getRerankModel());
                ObjectNode input = body.putObject("input");
                input.put("query", query);
                input.set("documents", objectMapper.valueToTree(documents));
                ObjectNode parameters = body.putObject("parameters");
                parameters.put("return_documents", false);
                parameters.put("top_n", limit);
            } else {
                body.put("model", model.getRerankModel());
                body.put("query", query);
                body.set("documents", objectMapper.valueToTree(documents));
                body.put("top_n", limit);
                if ("voyage".equals(provider)) {
                    body.remove("top_n");
                    body.put("top_k", limit);
                }
            }

            endpoint = createRerankUri(provider, model.getRerankBaseUrl());
            log.info("开始调用重排模型 provider={} model={} endpoint={} candidates={}",
                    provider, model.getRerankModel(), endpoint, documents.size());
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (StringUtils.hasText(model.getApiKey())) {
                request.header("Authorization", "Bearer " + model.getApiKey());
            }
            HttpResponse<String> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("重排模型调用返回错误 provider={} model={} endpoint={} status={} durationMs={}",
                        provider, model.getRerankModel(), endpoint, response.statusCode(),
                        Duration.ofNanos(System.nanoTime() - startTime).toMillis());
                throw new IllegalStateException("请求 " + endpoint.getPath() + " 返回 HTTP "
                        + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = "dashscope".equals(provider)
                    ? root.path("output").path("results")
                    : "voyage".equals(provider) ? root.path("data") : root.path("results");
            if (!results.isArray()) {
                throw new IllegalStateException("响应中缺少重排结果");
            }

            List<Result> ranked = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (JsonNode result : results) {
                JsonNode indexNode = result.path("index");
                JsonNode scoreNode = result.has("relevance_score")
                        ? result.path("relevance_score") : result.path("score");
                if (!indexNode.canConvertToInt() || !scoreNode.isNumber()) {
                    throw new IllegalStateException("重排结果缺少有效的索引或相关度分数");
                }
                int index = indexNode.intValue();
                if (index < 0 || index >= documents.size() || !seen.add(index)) {
                    throw new IllegalStateException("重排结果包含重复或越界的文档索引");
                }
                ranked.add(new Result(index, scoreNode.doubleValue()));
            }
            if (ranked.isEmpty()) {
                throw new IllegalStateException("重排服务未返回任何结果");
            }

            // top_n < documents.size() 时 API 只返回前 N 条，未返回的不属于"未打分"，不再补 0 分；
            // 按分数降序（稳定排序，同分保持 API 返回顺序）
            ranked.sort(java.util.Comparator.comparingDouble(Result::score).reversed());
            if (ranked.size() > limit) ranked = new ArrayList<>(ranked.subList(0, limit));
            log.info("重排模型调用成功 provider={} model={} endpoint={} candidates={} ranked={} durationMs={}",
                    provider, model.getRerankModel(), endpoint, documents.size(), ranked.size(),
                    Duration.ofNanos(System.nanoTime() - startTime).toMillis());
            return ranked;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("重排模型调用被中断 provider={} model={} endpoint={} durationMs={}",
                    provider, model.getRerankModel(), endpoint,
                    Duration.ofNanos(System.nanoTime() - startTime).toMillis());
            throw new IllegalStateException("调用重排模型被中断", e);
        } catch (Exception e) {
            log.error("重排模型调用失败 provider={} model={} endpoint={} durationMs={}",
                    provider, model.getRerankModel(), endpoint,
                    Duration.ofNanos(System.nanoTime() - startTime).toMillis(), e);
            if (e instanceof IllegalStateException state) {
                throw new IllegalStateException("调用重排模型失败: " + state.getMessage(), state);
            }
            throw new IllegalStateException("调用重排模型失败: " + e.getMessage(), e);
        }
    }

    static URI createRerankUri(String provider, String baseUrl) throws Exception {
        URI baseUri = URI.create(baseUrl.trim());
        String scheme = baseUri.getScheme();
        if (!Set.of("http", "https").contains(scheme == null ? "" : scheme.toLowerCase(Locale.ROOT))
                || !StringUtils.hasText(baseUri.getHost())
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null) {
            throw new BizException("重排服务地址必须是无凭据、无查询参数的 HTTP 或 HTTPS URL");
        }

        String path = baseUri.getPath() == null ? "" : baseUri.getPath().replaceAll("/+$", "");
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        String endpointPath;
        if ("dashscope".equals(normalizedProvider)) {
            if (path.matches("(?i).*/text-rerank/text-rerank$")) {
                endpointPath = path;
            } else if (path.matches("(?i).*/services/rerank/text-rerank$")) {
                endpointPath = path + "/text-rerank";
            } else if (path.matches("(?i).*/api/v1$")) {
                endpointPath = path + "/services/rerank/text-rerank/text-rerank";
            } else {
                endpointPath = path + "/api/v1/services/rerank/text-rerank/text-rerank";
            }
        } else if (path.matches("(?i).*/rerank$")) {
            endpointPath = path;
        } else if ("cohere".equals(normalizedProvider)) {
            endpointPath = path.matches("(?i).*/v[12]$")
                    ? path + "/rerank" : path + "/v2/rerank";
        } else if (path.matches("(?i).*/v[0-9]+$")) {
            endpointPath = path + "/rerank";
        } else if (Set.of("jina", "siliconflow", "voyage").contains(normalizedProvider)) {
            endpointPath = path + "/v1/rerank";
        } else {
            endpointPath = path + "/rerank";
        }
        return new URI(scheme.toLowerCase(Locale.ROOT), null, baseUri.getHost(),
                baseUri.getPort(), endpointPath, null, null);
    }
}
