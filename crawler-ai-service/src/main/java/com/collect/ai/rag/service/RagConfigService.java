package com.collect.ai.rag.service;

import com.collect.ai.entity.AiRagConfig;
import com.collect.ai.mapper.AiRagConfigMapper;
import com.collect.ai.rag.config.RagProperties;
import com.collect.ai.rag.dto.RagConfigRequest;
import com.collect.ai.rag.dto.RagConfigResponse;
import com.collect.ai.rag.initializer.RagIndexInitializer;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RagConfigService {

    private static final Set<String> PROVIDERS = Set.of("ollama", "openai");
    private static final String CONFIG_ID = "default";

    private final AiRagConfigMapper configMapper;
    private final RagProperties properties;
    private final RagIndexInitializer indexInitializer;
    private final Environment environment;

    @jakarta.annotation.PostConstruct
    public void load() {
        String env = resolveEnvironment();
        AiRagConfig config = configMapper.selectById(env);
        if (config == null) {
            config = defaultConfig(env);
            try {
                configMapper.insert(config);
            } catch (DuplicateKeyException ignored) {
                config = configMapper.selectById(env);
            }
        }
        if (config == null) {
            throw new IllegalStateException("RAG 数据库配置初始化失败，environment=" + env);
        }
        apply(config);
    }

    public RagConfigResponse getConfig() {
        AiRagConfig config = requireConfig();
        RagConfigResponse response = new RagConfigResponse();
        response.setEnvironment(config.getEnvironment());
        response.setEnabled(config.getEnabled());
        response.setSourceIndex(config.getSourceIndex());
        response.setEmbeddingProvider(config.getEmbeddingProvider());
        response.setEmbeddingBaseUrl(config.getEmbeddingBaseUrl());
        response.setEmbeddingModel(config.getEmbeddingModel());
        response.setDimensions(config.getDimensions());
        response.setChatProvider(config.getChatProvider());
        response.setChatBaseUrl(config.getChatBaseUrl());
        response.setChatModel(config.getChatModel());
        response.setApiKeyConfigured(StringUtils.hasText(config.getApiKey()));
        response.setTopK(config.getTopK());
        response.setChunkSize(config.getChunkSize());
        response.setChunkOverlap(config.getChunkOverlap());
        response.setVectorIndexPrefix(config.getVectorIndexPrefix());
        response.setVectorFieldPrefix(config.getVectorFieldPrefix());
        response.setVectorIndex(properties.resolveVectorIndex());
        response.setVectorField(properties.resolveVectorField());
        return response;
    }

    @Transactional
    public RagConfigResponse update(RagConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("配置请求不能为空");
        }
        AiRagConfig config = requireConfig();
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getSourceIndex() != null) config.setSourceIndex(request.getSourceIndex().trim());
        if (request.getEmbeddingProvider() != null) config.setEmbeddingProvider(
                request.getEmbeddingProvider().trim().toLowerCase(Locale.ROOT));
        if (request.getEmbeddingBaseUrl() != null) config.setEmbeddingBaseUrl(
                request.getEmbeddingBaseUrl().trim());
        if (request.getEmbeddingModel() != null) config.setEmbeddingModel(request.getEmbeddingModel().trim());
        if (request.getDimensions() != null) config.setDimensions(request.getDimensions());
        if (request.getChatProvider() != null) config.setChatProvider(
                request.getChatProvider().trim().toLowerCase(Locale.ROOT));
        if (request.getChatBaseUrl() != null) config.setChatBaseUrl(request.getChatBaseUrl().trim());
        if (request.getChatModel() != null) config.setChatModel(request.getChatModel().trim());
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            config.setApiKey("");
        } else if (request.getApiKey() != null) {
            config.setApiKey(request.getApiKey().trim());
        }
        if (request.getTopK() != null) config.setTopK(request.getTopK());
        if (request.getChunkSize() != null) config.setChunkSize(request.getChunkSize());
        if (request.getChunkOverlap() != null) config.setChunkOverlap(request.getChunkOverlap());
        if (request.getVectorIndexPrefix() != null) config.setVectorIndexPrefix(
                request.getVectorIndexPrefix().trim());
        if (request.getVectorFieldPrefix() != null) config.setVectorFieldPrefix(
                request.getVectorFieldPrefix().trim());

        try {
            validate(config);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        config.setUpdateTime(LocalDateTime.now());
        RagProperties.Rag previous = properties.getRag();
        apply(config);
        try {
            if (Boolean.TRUE.equals(config.getEnabled())) {
                indexInitializer.ensureVectorIndex();
            }
            if (configMapper.updateById(config) != 1) {
                throw new IllegalStateException("保存 RAG 数据库配置失败");
            }
        } catch (RuntimeException e) {
            properties.replace(previous);
            throw e;
        }
        return getConfig();
    }

    private AiRagConfig requireConfig() {
        AiRagConfig config = configMapper.selectById(resolveEnvironment());
        if (config == null) {
            throw new IllegalStateException("未找到当前环境的 RAG 数据库配置");
        }
        return config;
    }

    private void apply(AiRagConfig config) {
        RagProperties.Rag rag = new RagProperties.Rag();
        rag.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        rag.setSourceIndex(config.getSourceIndex());
        rag.setEmbeddingProvider(config.getEmbeddingProvider());
        rag.setEmbeddingBaseUrl(config.getEmbeddingBaseUrl());
        rag.setEmbeddingModel(config.getEmbeddingModel());
        rag.setDimensions(config.getDimensions());
        rag.setChatProvider(config.getChatProvider());
        rag.setChatBaseUrl(config.getChatBaseUrl());
        rag.setChatModel(config.getChatModel());
        rag.setApiKey(config.getApiKey());
        rag.setTopK(config.getTopK());
        rag.setChunkSize(config.getChunkSize());
        rag.setChunkOverlap(config.getChunkOverlap());
        rag.setVectorIndexPrefix(config.getVectorIndexPrefix());
        rag.setVectorFieldPrefix(config.getVectorFieldPrefix());
        properties.replace(rag);
    }

    private AiRagConfig defaultConfig(String env) {
        RagProperties.Rag defaults = new RagProperties.Rag();
        AiRagConfig config = new AiRagConfig();
        config.setEnvironment(env);
        config.setEnabled(defaults.isEnabled());
        config.setSourceIndex("local".equals(env) ? "spider_content_local" : defaults.getSourceIndex());
        config.setEmbeddingProvider(defaults.getEmbeddingProvider());
        config.setEmbeddingBaseUrl(defaults.getEmbeddingBaseUrl());
        config.setEmbeddingModel(defaults.getEmbeddingModel());
        config.setDimensions(defaults.getDimensions());
        config.setChatProvider(defaults.getChatProvider());
        config.setChatBaseUrl(defaults.getChatBaseUrl());
        config.setChatModel(defaults.getChatModel());
        config.setApiKey(defaults.getApiKey());
        config.setTopK(defaults.getTopK());
        config.setChunkSize(defaults.getChunkSize());
        config.setChunkOverlap(defaults.getChunkOverlap());
        config.setVectorIndexPrefix("local".equals(env)
                ? "spider_content_vector_local" : defaults.getVectorIndexPrefix());
        config.setVectorFieldPrefix(defaults.getVectorFieldPrefix());
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        return config;
    }

    private void validate(AiRagConfig config) {
        if (!StringUtils.hasText(config.getSourceIndex())
                || !StringUtils.hasText(config.getEmbeddingModel())
                || !StringUtils.hasText(config.getChatModel())
                || !StringUtils.hasText(config.getVectorIndexPrefix())
                || !StringUtils.hasText(config.getVectorFieldPrefix())) {
            throw new IllegalArgumentException("索引、模型和向量字段配置不能为空");
        }
        if (!config.getSourceIndex().matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("ES 源索引名称不合法");
        }
        if (!PROVIDERS.contains(config.getEmbeddingProvider())
                || !PROVIDERS.contains(config.getChatProvider())) {
            throw new IllegalArgumentException("模型提供方仅支持 ollama 或 openai");
        }
        validateHttpUrl(config.getEmbeddingBaseUrl(), "Embedding 地址");
        validateHttpUrl(config.getChatBaseUrl(), "聊天地址");
        if (config.getDimensions() == null || config.getDimensions() < 1
                || config.getTopK() == null || config.getTopK() < 1 || config.getTopK() > 20
                || config.getChunkSize() == null || config.getChunkSize() < 200
                || config.getChunkOverlap() == null || config.getChunkOverlap() < 0
                || config.getChunkOverlap() >= config.getChunkSize()) {
            throw new IllegalArgumentException("向量维度、topK 或分块参数不合法");
        }
        if (config.getApiKey() != null && config.getApiKey().length() > 1024) {
            throw new IllegalArgumentException("API Key 长度不能超过 1024 个字符");
        }
        if (!config.getVectorIndexPrefix().matches("[a-z0-9_-]+")
                || !config.getVectorFieldPrefix().matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("向量索引和字段前缀仅允许字母、数字、下划线和连字符");
        }
    }

    private void validateHttpUrl(String value, String name) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || !Set.of("http", "https").contains(scheme)
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException(name + "必须是有效的 HTTP 或 HTTPS URL");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + "必须是有效的 HTTP 或 HTTPS URL", e);
        }
    }

    private String resolveEnvironment() {
        return java.util.Arrays.asList(environment.getActiveProfiles()).contains("local")
                ? "local" : CONFIG_ID;
    }
}
