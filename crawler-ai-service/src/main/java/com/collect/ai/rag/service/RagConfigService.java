package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiRagConfig;
import com.collect.ai.entity.AiRagChatModel;
import com.collect.ai.entity.AiRagVectorModel;
import com.collect.ai.mapper.AiRagConfigMapper;
import com.collect.ai.mapper.AiRagChatModelMapper;
import com.collect.ai.mapper.AiRagVectorModelMapper;
import com.collect.ai.rag.config.RagProperties;
import com.collect.ai.rag.dto.RagConfigRequest;
import com.collect.ai.rag.dto.RagConfigResponse;
import com.collect.ai.rag.initializer.RagIndexInitializer;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagConfigService {

    private static final Set<String> EMBEDDING_PROVIDERS = Set.of("ollama", "openai");
    private static final Set<String> CHAT_PROVIDERS = Set.of("ollama", "openai", "vllm");
    private static final String CONFIG_ID = "default";

    private final AiRagConfigMapper configMapper;
    private final AiRagChatModelMapper chatModelMapper;
    private final AiRagVectorModelMapper vectorModelMapper;
    private final RagProperties properties;
    private final RagIndexInitializer indexInitializer;
    private final Environment environment;

    @Value("${minio.bucket:crawler-knowledge}")
    private String uploadBucket;

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
        ensureVectorModel(config);
        ensureChatModel(config);
        apply(config);
    }

    public RagConfigResponse getConfig() {
        AiRagConfig config = requireConfig();
        RagConfigResponse response = new RagConfigResponse();
        response.setEnvironment(config.getEnvironment());
        response.setSourceIndex(properties.resolveSourceIndex());
        response.setEmbeddingProvider(config.getEmbeddingProvider());
        response.setEmbeddingBaseUrl(config.getEmbeddingBaseUrl());
        response.setEmbeddingModel(config.getEmbeddingModel());
        response.setDimensions(config.getDimensions());
        response.setVectorModelId(config.getVectorModelId());
        response.setChatModelId(config.getChatModelId());
        response.setChatProvider(config.getChatProvider());
        response.setChatBaseUrl(config.getChatBaseUrl());
        response.setChatModel(config.getChatModel());
        response.setApiKeyConfigured(StringUtils.hasText(config.getApiKey()));
        response.setTopK(config.getTopK());
        response.setMaxChunksPerDocument(config.getMaxChunksPerDocument() == null
                ? 1 : config.getMaxChunksPerDocument());
        response.setVectorIndexName(properties.resolveVectorIndex());
        response.setVectorIndexPrefix(properties.getRag().getVectorIndexPrefix());
        response.setVectorIndex(properties.resolveVectorIndex());
        response.setVectorField(properties.resolveVectorField());
        response.setUploadBucket(uploadBucket);
        return response;
    }

    @Transactional
    public RagConfigResponse update(RagConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("配置请求不能为空");
        }
        AiRagConfig config = requireConfig();
        RagProperties.Rag previous = properties.getRag();
        String previousVectorIndex = properties.resolveVectorIndex();
        String previousSourceIndex = previous.getSourceIndex();
        AiRagVectorModel legacyUpdatedModel = null;
        AiRagChatModel legacyUpdatedChatModel = null;
        if (request.getVectorModelId() != null) {
            AiRagVectorModel model = vectorModelMapper.selectById(request.getVectorModelId());
            if (model == null) {
                throw new BizException("所选向量模型不存在");
            }
            config.setVectorModelId(model.getId());
            applyVectorModel(config, model);
        } else {
            if (request.getEmbeddingProvider() != null) config.setEmbeddingProvider(
                    request.getEmbeddingProvider().trim().toLowerCase(Locale.ROOT));
            if (request.getEmbeddingBaseUrl() != null) config.setEmbeddingBaseUrl(
                    request.getEmbeddingBaseUrl().trim());
            if (request.getEmbeddingModel() != null) config.setEmbeddingModel(request.getEmbeddingModel().trim());
            if (request.getDimensions() != null) config.setDimensions(request.getDimensions());
        }
        if (request.getChatModelId() != null) {
            AiRagChatModel model = chatModelMapper.selectById(request.getChatModelId());
            if (model == null) {
                throw new BizException("所选聊天模型不存在");
            }
            config.setChatModelId(model.getId());
            applyChatModel(config, model);
        } else {
            if (request.getChatProvider() != null) config.setChatProvider(
                    request.getChatProvider().trim().toLowerCase(Locale.ROOT));
            if (request.getChatBaseUrl() != null) config.setChatBaseUrl(request.getChatBaseUrl().trim());
            if (request.getChatModel() != null) config.setChatModel(request.getChatModel().trim());
            if (Boolean.TRUE.equals(request.getClearApiKey())) {
                config.setApiKey("");
            } else if (request.getApiKey() != null) {
                config.setApiKey(request.getApiKey().trim());
            }
            if (request.getChatProvider() != null || request.getChatBaseUrl() != null
                    || request.getChatModel() != null || request.getApiKey() != null
                    || Boolean.TRUE.equals(request.getClearApiKey())) {
                legacyUpdatedChatModel = chatModelMapper.selectById(config.getChatModelId());
                if (legacyUpdatedChatModel == null) {
                    throw new BizException("RAG 配置引用的聊天模型不存在");
                }
                applyChatModel(legacyUpdatedChatModel, config);
            }
        }
        if (request.getVectorModelId() == null) {
            if (request.getTopK() != null) config.setTopK(request.getTopK());
            if (request.getEmbeddingProvider() != null || request.getEmbeddingBaseUrl() != null
                    || request.getEmbeddingModel() != null || request.getDimensions() != null
                    || request.getTopK() != null) {
                legacyUpdatedModel = vectorModelMapper.selectById(config.getVectorModelId());
                if (legacyUpdatedModel == null) {
                    throw new BizException("RAG 配置引用的向量模型不存在");
                }
                applyVectorModel(legacyUpdatedModel, config);
            }
        }

        if (request.getMaxChunksPerDocument() != null) {
            config.setMaxChunksPerDocument(request.getMaxChunksPerDocument());
        }

        try {
            validate(config);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        if (legacyUpdatedModel != null) {
            legacyUpdatedModel.setUpdateTime(LocalDateTime.now());
            if (vectorModelMapper.updateById(legacyUpdatedModel) != 1) {
                throw new IllegalStateException("保存向量模型失败");
            }
        }
        if (legacyUpdatedChatModel != null) {
            legacyUpdatedChatModel.setUpdateTime(LocalDateTime.now());
            if (chatModelMapper.updateById(legacyUpdatedChatModel) != 1) {
                throw new IllegalStateException("保存聊天模型失败");
            }
        }
        config.setUpdateTime(LocalDateTime.now());
        apply(config);
        try {
            String vectorIndex = properties.resolveVectorIndex();
            if (vectorIndex.equals(properties.resolveSourceIndex())) {
                throw new BizException("向量索引不能与 ES 源索引相同");
            }
            indexInitializer.ensureVectorIndex();
            if (configMapper.updateById(config) != 1) {
                throw new IllegalStateException("保存 RAG 数据库配置失败");
            }
            deleteOldVectorIndexAfterCommit(previousVectorIndex, vectorIndex, previousSourceIndex);
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
        AiRagVectorModel model = vectorModelMapper.selectById(config.getVectorModelId());
        if (model == null) {
            throw new IllegalStateException("RAG 配置引用的向量模型不存在");
        }
        applyVectorModel(config, model);
        AiRagChatModel chatModel = chatModelMapper.selectById(config.getChatModelId());
        if (chatModel == null) {
            throw new IllegalStateException("RAG 配置引用的聊天模型不存在");
        }
        applyChatModel(config, chatModel);
        RagProperties.Rag rag = new RagProperties.Rag();
        rag.setSourceIndex(properties.resolveSourceIndex());
        rag.setEmbeddingProvider(config.getEmbeddingProvider());
        rag.setEmbeddingBaseUrl(config.getEmbeddingBaseUrl());
        rag.setEmbeddingModel(config.getEmbeddingModel());
        rag.setEmbeddingApiKey(model.getApiKey());
        rag.setDimensions(config.getDimensions());
        rag.setChatProvider(config.getChatProvider());
        rag.setChatBaseUrl(config.getChatBaseUrl());
        rag.setChatModel(config.getChatModel());
        rag.setContextLength(chatModel.getContextLength() == null
                ? 8192 : chatModel.getContextLength());
        rag.setApiKey(config.getApiKey());
        rag.setTopK(config.getTopK());
        rag.setMaxChunksPerDocument(config.getMaxChunksPerDocument() == null
                ? 1 : config.getMaxChunksPerDocument());
        rag.setVectorIndexPrefix(properties.getRag().getVectorIndexPrefix());
        rag.setVectorIndexEnvironment(properties.getRag().getVectorIndexEnvironment());
        properties.replace(rag);
    }

    @Transactional
    public void refreshSelectedChatModel(Long chatModelId) {
        List<AiRagConfig> configs = configMapper.selectList(
                new LambdaQueryWrapper<AiRagConfig>()
                        .eq(AiRagConfig::getChatModelId, chatModelId));
        if (configs.isEmpty()) {
            return;
        }
        AiRagChatModel model = chatModelMapper.selectById(chatModelId);
        if (model == null) {
            throw new IllegalStateException("RAG 配置引用的聊天模型不存在");
        }
        RagProperties.Rag previous = properties.getRag();
        boolean propertiesUpdated = false;
        try {
            for (AiRagConfig config : configs) {
                applyChatModel(config, model);
                try {
                    validate(config);
                } catch (IllegalArgumentException e) {
                    throw new BizException(e.getMessage());
                }
                if (resolveEnvironment().equals(config.getEnvironment())) {
                    apply(config);
                    propertiesUpdated = true;
                }
                config.setUpdateTime(LocalDateTime.now());
                if (configMapper.updateById(config) != 1) {
                    throw new IllegalStateException("保存 RAG 数据库配置失败");
                }
            }
        } catch (RuntimeException e) {
            if (propertiesUpdated) {
                properties.replace(previous);
            }
            throw e;
        }
    }

    private void ensureChatModel(AiRagConfig config) {
        AiRagChatModel model = config.getChatModelId() == null
                ? null : chatModelMapper.selectById(config.getChatModelId());
        if (model == null && config.getChatModelId() != null) {
            throw new IllegalStateException("RAG 配置引用的聊天模型不存在");
        }
        if (model == null) {
            model = new AiRagChatModel();
            model.setName("默认聊天模型-" + config.getEnvironment());
            model.setChatProvider(config.getChatProvider());
            model.setChatBaseUrl(config.getChatBaseUrl());
            model.setChatModel(config.getChatModel());
            model.setApiKey(config.getApiKey());
            model.setCreateTime(LocalDateTime.now());
            model.setUpdateTime(model.getCreateTime());
            if (chatModelMapper.insert(model) != 1) {
                throw new IllegalStateException("初始化聊天模型失败");
            }
            config.setChatModelId(model.getId());
            if (configMapper.updateById(config) != 1) {
                throw new IllegalStateException("保存 RAG 聊天模型引用失败");
            }
        }
        applyChatModel(config, model);
    }

    private static void applyChatModel(AiRagConfig config, AiRagChatModel model) {
        config.setChatProvider(model.getChatProvider());
        config.setChatBaseUrl(model.getChatBaseUrl());
        config.setChatModel(model.getChatModel());
        config.setApiKey(model.getApiKey());
    }

    private static void applyChatModel(AiRagChatModel model, AiRagConfig config) {
        model.setChatProvider(config.getChatProvider());
        model.setChatBaseUrl(config.getChatBaseUrl());
        model.setChatModel(config.getChatModel());
        model.setApiKey(config.getApiKey());
    }

    @Transactional
    public void refreshSelectedVectorModel(Long vectorModelId) {
        AiRagConfig config = requireConfig();
        if (!vectorModelId.equals(config.getVectorModelId())) {
            return;
        }
        RagProperties.Rag previous = properties.getRag();
        String previousVectorIndex = properties.resolveVectorIndex();
        String previousSourceIndex = previous.getSourceIndex();
        AiRagVectorModel model = vectorModelMapper.selectById(vectorModelId);
        if (model == null) {
            throw new IllegalStateException("RAG 配置引用的向量模型不存在");
        }
        applyVectorModel(config, model);
        try {
            validate(config);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        apply(config);
        try {
            String vectorIndex = properties.resolveVectorIndex();
            if (vectorIndex.equals(properties.resolveSourceIndex())) {
                throw new BizException("向量索引不能与 ES 源索引相同");
            }
            indexInitializer.ensureVectorIndex();
            if (configMapper.updateById(config) != 1) {
                throw new IllegalStateException("保存 RAG 数据库配置失败");
            }
            deleteOldVectorIndexAfterCommit(previousVectorIndex, vectorIndex, previousSourceIndex);
        } catch (RuntimeException e) {
            properties.replace(previous);
            throw e;
        }
    }

    private void deleteOldVectorIndexAfterCommit(
            String previousIndex, String currentIndex, String previousSourceIndex) {
        if (previousIndex.equals(currentIndex)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("无法在事务提交后清理旧 RAG 向量索引");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (previousIndex.equals(previousSourceIndex)) {
                    log.warn("旧 RAG 向量索引名称与 ES 源索引相同，跳过删除以保护源数据: {}",
                            previousIndex);
                    return;
                }
                if (isIndexReferencedByAnotherEnvironment(previousIndex)) {
                    log.warn("旧 RAG 向量索引仍被其他环境使用，跳过删除: {}", previousIndex);
                    return;
                }
                indexInitializer.deleteVectorIndex(previousIndex);
            }
        });
    }

    private boolean isIndexReferencedByAnotherEnvironment(String index) {
        return configMapper.selectList(new LambdaQueryWrapper<AiRagConfig>()
                        .ne(AiRagConfig::getEnvironment, resolveEnvironment()))
                .stream()
                        .anyMatch(config -> index.equals(properties.resolveSourceIndex())
                        || index.equals(resolveVectorIndex(config)));
    }

    private String resolveVectorIndex(AiRagConfig config) {
        if (StringUtils.hasText(config.getVectorIndex())) {
            return config.getVectorIndex().trim();
        }
        String model = config.getEmbeddingModel() == null
                ? "unknown"
                : config.getEmbeddingModel().toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return config.getVectorIndexPrefix() + "_" + model;
    }

    private void ensureVectorModel(AiRagConfig config) {
        AiRagVectorModel model = config.getVectorModelId() == null
                ? null : vectorModelMapper.selectById(config.getVectorModelId());
        if (model == null && config.getVectorModelId() != null) {
            throw new IllegalStateException("RAG 配置引用的向量模型不存在");
        }
        if (model == null) {
            model = new AiRagVectorModel();
            model.setName("默认向量模型-" + config.getEnvironment());
            model.setEmbeddingProvider(config.getEmbeddingProvider());
            model.setEmbeddingBaseUrl(config.getEmbeddingBaseUrl());
            model.setEmbeddingModel(config.getEmbeddingModel());
            model.setDimensions(config.getDimensions());
            model.setTopK(config.getTopK());
            model.setCreateTime(LocalDateTime.now());
            model.setUpdateTime(model.getCreateTime());
            if (vectorModelMapper.insert(model) != 1) {
                throw new IllegalStateException("初始化向量模型失败");
            }
            config.setVectorModelId(model.getId());
            if (configMapper.updateById(config) != 1) {
                throw new IllegalStateException("保存 RAG 向量模型引用失败");
            }
        }
        applyVectorModel(config, model);
    }

    private static void applyVectorModel(AiRagConfig config, AiRagVectorModel model) {
        config.setEmbeddingProvider(model.getEmbeddingProvider());
        config.setEmbeddingBaseUrl(model.getEmbeddingBaseUrl());
        config.setEmbeddingModel(model.getEmbeddingModel());
        config.setDimensions(model.getDimensions());
        config.setTopK(model.getTopK());
    }

    private static void applyVectorModel(AiRagVectorModel model, AiRagConfig config) {
        model.setEmbeddingProvider(config.getEmbeddingProvider());
        model.setEmbeddingBaseUrl(config.getEmbeddingBaseUrl());
        model.setEmbeddingModel(config.getEmbeddingModel());
        model.setDimensions(config.getDimensions());
        model.setTopK(config.getTopK());
    }

    private AiRagConfig defaultConfig(String env) {
        RagProperties.Rag defaults = new RagProperties.Rag();
        AiRagConfig config = new AiRagConfig();
        config.setEnvironment(env);
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
        config.setMaxChunksPerDocument(defaults.getMaxChunksPerDocument());
        config.setVectorIndex(resolveDefaultVectorIndex(env));
        config.setVectorIndexPrefix(defaults.getVectorIndexPrefix());
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        return config;
    }

    private String resolveDefaultVectorIndex(String env) {
        String environmentSuffix = "local".equals(env) ? "_local" : "";
        return properties.getRag().getVectorIndexPrefix() + environmentSuffix;
    }

    private void validate(AiRagConfig config) {
        if (!StringUtils.hasText(config.getEmbeddingModel())
                || !StringUtils.hasText(config.getChatModel())) {
            throw new IllegalArgumentException("模型配置不能为空");
        }
        if (!EMBEDDING_PROVIDERS.contains(config.getEmbeddingProvider())
                || !CHAT_PROVIDERS.contains(config.getChatProvider())) {
            throw new IllegalArgumentException("Embedding 提供方仅支持 ollama 或 openai；聊天模型提供方仅支持 ollama、openai 或 vllm");
        }
        validateHttpUrl(config.getEmbeddingBaseUrl(), "Embedding 地址");
        validateHttpUrl(config.getChatBaseUrl(), "聊天地址");
        if (config.getDimensions() == null || config.getDimensions() < 1
                || config.getTopK() == null || config.getTopK() < 1 || config.getTopK() > 20) {
            throw new IllegalArgumentException("向量维度或 topK 不合法");
        }
        if (config.getMaxChunksPerDocument() != null
                && (config.getMaxChunksPerDocument() < 1 || config.getMaxChunksPerDocument() > 20)) {
            throw new IllegalArgumentException("文档命中返回条数须在 1-20 之间");
        }
        if (config.getApiKey() != null && config.getApiKey().length() > 1024) {
            throw new IllegalArgumentException("API Key 长度不能超过 1024 个字符");
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
