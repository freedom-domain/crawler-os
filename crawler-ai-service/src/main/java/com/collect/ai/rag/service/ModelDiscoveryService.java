package com.collect.ai.rag.service;

import com.collect.ai.entity.AiRagChatModel;
import com.collect.ai.entity.AiRagVectorModel;
import com.collect.ai.mapper.AiRagChatModelMapper;
import com.collect.ai.mapper.AiRagVectorModelMapper;
import com.collect.ai.rag.dto.ModelDiscoveryRequest;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModelDiscoveryService {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final AiRagVectorModelMapper vectorModelMapper;
    private final AiRagChatModelMapper chatModelMapper;
    private final ObjectMapper objectMapper;

    public List<String> discoverVectorModels(ModelDiscoveryRequest request) {
        validateRequest(request, Set.of("ollama", "openai"));
        String apiKey = request.getApiKey();
        if (!StringUtils.hasText(apiKey) && request.getModelId() != null) {
            AiRagVectorModel model = vectorModelMapper.selectById(request.getModelId());
            if (model == null) throw new BizException("向量模型不存在");
            apiKey = model.getApiKey();
        }
        return fetchModels(request.getProvider(), request.getBaseUrl(), apiKey);
    }

    public List<String> discoverChatModels(ModelDiscoveryRequest request) {
        validateRequest(request, Set.of("ollama", "openai", "vllm"));
        String apiKey = request.getApiKey();
        if (!StringUtils.hasText(apiKey) && request.getModelId() != null) {
            AiRagChatModel model = chatModelMapper.selectById(request.getModelId());
            if (model == null) throw new BizException("聊天模型不存在");
            apiKey = model.getApiKey();
        }
        return fetchModels(request.getProvider(), request.getBaseUrl(), apiKey);
    }

    private List<String> fetchModels(String provider, String baseUrl, String apiKey) {
        try {
            URI requestUri = createModelsUri(provider, baseUrl);
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(requestUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");
            if (StringUtils.hasText(apiKey)) {
                request.header("Authorization", "Bearer " + apiKey.trim());
            }
            HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(
                    request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (java.io.InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new BizException("拉取模型列表失败，服务返回 HTTP " + response.statusCode());
                }
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new BizException("模型列表响应超过允许大小");
                }
                return parseModelNames(objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8)),
                        "ollama".equalsIgnoreCase(provider));
            }
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("拉取模型列表被中断");
        } catch (Exception e) {
            throw new BizException("拉取模型列表失败，请检查服务地址、API Key 和提供方接口");
        }
    }

    private static URI createModelsUri(String provider, String baseUrl) throws Exception {
        URI baseUri = URI.create(baseUrl.trim());
        String scheme = baseUri.getScheme();
        if (!Set.of("http", "https").contains(scheme == null ? "" : scheme.toLowerCase(Locale.ROOT))
                || !StringUtils.hasText(baseUri.getHost())
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null) {
            throw new BizException("服务地址必须是无凭据、无查询参数的 HTTP 或 HTTPS URL");
        }
        String path = baseUri.getPath() == null ? "" : baseUri.getPath().replaceAll("/+$", "");
        String modelsPath;
        if ("ollama".equalsIgnoreCase(provider)) {
            path = path.replaceFirst("(?i)/v1$", "");
            modelsPath = path + "/api/tags";
        } else {
            modelsPath = path + "/models";
        }
        return new URI(scheme.toLowerCase(Locale.ROOT), null, baseUri.getHost(),
                baseUri.getPort(), modelsPath, null, null);
    }

    private List<String> parseModelNames(JsonNode root, boolean ollama) {
        JsonNode models = root.path(ollama ? "models" : "data");
        if (!models.isArray()) {
            throw new BizException("模型服务返回的数据格式不受支持");
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode model : models) {
            String name = model.path(ollama ? "name" : "id").asText("").trim();
            if (!name.isEmpty() && name.length() <= 256) names.add(name);
            if (names.size() >= 1000) break;
        }
        return new ArrayList<>(names);
    }

    private static void validateRequest(ModelDiscoveryRequest request, Set<String> providers) {
        if (request == null || !StringUtils.hasText(request.getProvider())
                || !StringUtils.hasText(request.getBaseUrl())) {
            throw new BizException("模型提供方和服务地址不能为空");
        }
        if (!providers.contains(request.getProvider().trim().toLowerCase(Locale.ROOT))) {
            throw new BizException("不支持的模型提供方");
        }
        if (request.getBaseUrl().length() > 512
                || (request.getApiKey() != null && request.getApiKey().length() > 1024)) {
            throw new BizException("服务地址或 API Key 长度超出限制");
        }
    }
}
