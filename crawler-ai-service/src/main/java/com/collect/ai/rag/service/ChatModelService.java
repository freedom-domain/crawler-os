package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.ai.entity.AiRagChatModel;
import com.collect.ai.entity.AiRagConfig;
import com.collect.ai.mapper.AiRagChatModelMapper;
import com.collect.ai.mapper.AiRagConfigMapper;
import com.collect.ai.rag.dto.ChatModelRequest;
import com.collect.ai.rag.dto.ChatModelResponse;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatModelService {

    private static final Set<String> PROVIDERS = Set.of("ollama", "openai", "vllm");
    private static final int MIN_CONTEXT_LENGTH = 1024;
    private static final int MAX_CONTEXT_LENGTH = 2_000_000;
    private static final int DEFAULT_CONTEXT_LENGTH = 8192;

    private final AiRagChatModelMapper chatModelMapper;
    private final AiRagConfigMapper configMapper;
    private final RagConfigService ragConfigService;

    public List<ChatModelResponse> list() {
        return chatModelMapper.selectList(
                        new LambdaQueryWrapper<AiRagChatModel>()
                                .orderByAsc(AiRagChatModel::getId))
                .stream().map(ChatModelService::toResponse).toList();
    }

    public IPage<ChatModelResponse> page(long current, long size, String keyword,
                                         String provider, String apiKeyStatus) {
        LambdaQueryWrapper<AiRagChatModel> query = new LambdaQueryWrapper<AiRagChatModel>()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(AiRagChatModel::getName, keyword.trim())
                        .or().like(AiRagChatModel::getChatModel, keyword.trim())
                        .or().like(AiRagChatModel::getChatBaseUrl, keyword.trim()))
                .eq(StringUtils.hasText(provider), AiRagChatModel::getChatProvider, provider)
                .orderByAsc(AiRagChatModel::getId);
        if ("configured".equals(apiKeyStatus)) {
            query.isNotNull(AiRagChatModel::getApiKey).ne(AiRagChatModel::getApiKey, "");
        } else if ("unconfigured".equals(apiKeyStatus)) {
            query.and(wrapper -> wrapper.isNull(AiRagChatModel::getApiKey)
                    .or().eq(AiRagChatModel::getApiKey, ""));
        }
        return chatModelMapper.selectPage(new Page<>(safeCurrent(current), safeSize(size)), query)
                .convert(ChatModelService::toResponse);
    }

    public String getApiKey(Long id) {
        return requireModel(id).getApiKey();
    }

    @Transactional
    public void updateApiKey(Long id, String apiKey) {
        if (apiKey != null && apiKey.length() > 1024) {
            throw new BizException("API Key 长度不能超过 1024 个字符");
        }
        AiRagChatModel model = requireModel(id);
        model.setApiKey(apiKey == null ? "" : apiKey.trim());
        model.setUpdateTime(LocalDateTime.now());
        if (chatModelMapper.updateById(model) != 1) {
            throw new IllegalStateException("保存聊天模型 API Key 失败");
        }
        ragConfigService.refreshSelectedChatModel(id);
    }

    @Transactional
    public ChatModelResponse create(ChatModelRequest request) {
        validate(request);
        ensureUniqueName(request.getName(), null);
        AiRagChatModel model = fromRequest(request);
        model.setApiKey(request.getApiKey() == null ? "" : request.getApiKey().trim());
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(model.getCreateTime());
        if (chatModelMapper.insert(model) != 1) {
            throw new IllegalStateException("创建聊天模型失败");
        }
        return toResponse(model);
    }

    @Transactional
    public ChatModelResponse update(Long id, ChatModelRequest request) {
        validate(request);
        AiRagChatModel model = requireModel(id);
        ensureUniqueName(request.getName(), id);
        AiRagChatModel previous = copy(model);
        copyRequest(request, model);
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            model.setApiKey("");
        } else if (StringUtils.hasText(request.getApiKey())) {
            model.setApiKey(request.getApiKey().trim());
        }
        model.setUpdateTime(LocalDateTime.now());
        if (chatModelMapper.updateById(model) != 1) {
            throw new IllegalStateException("保存聊天模型失败");
        }
        try {
            ragConfigService.refreshSelectedChatModel(id);
        } catch (RuntimeException e) {
            chatModelMapper.updateById(previous);
            throw e;
        }
        return toResponse(model);
    }

    @Transactional
    public void delete(Long id) {
        requireModel(id);
        Long references = configMapper.selectCount(new LambdaQueryWrapper<AiRagConfig>()
                .eq(AiRagConfig::getChatModelId, id));
        if (references > 0) {
            throw new BizException("该聊天模型正被 AI 配置引用，不能删除");
        }
        if (chatModelMapper.deleteById(id) != 1) {
            throw new IllegalStateException("删除聊天模型失败");
        }
    }

    private AiRagChatModel requireModel(Long id) {
        AiRagChatModel model = chatModelMapper.selectById(id);
        if (model == null) {
            throw new BizException("聊天模型不存在");
        }
        return model;
    }

    private void ensureUniqueName(String name, Long excludedId) {
        LambdaQueryWrapper<AiRagChatModel> query = new LambdaQueryWrapper<AiRagChatModel>()
                .eq(AiRagChatModel::getName, name.trim());
        if (excludedId != null) {
            query.ne(AiRagChatModel::getId, excludedId);
        }
        if (chatModelMapper.selectCount(query) > 0) {
            throw new BizException("聊天模型配置名称已存在");
        }
    }

    private static AiRagChatModel fromRequest(ChatModelRequest request) {
        AiRagChatModel model = new AiRagChatModel();
        copyRequest(request, model);
        return model;
    }

    private static void copyRequest(ChatModelRequest request, AiRagChatModel model) {
        model.setName(request.getName().trim());
        model.setChatProvider(request.getChatProvider().trim().toLowerCase(Locale.ROOT));
        model.setChatBaseUrl(request.getChatBaseUrl().trim());
        model.setChatModel(request.getChatModel().trim());
        model.setContextLength(
                request.getContextLength() == null ? DEFAULT_CONTEXT_LENGTH : request.getContextLength());
    }

    private static void validate(ChatModelRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getChatProvider())
                || !StringUtils.hasText(request.getChatBaseUrl())
                || !StringUtils.hasText(request.getChatModel())) {
            throw new BizException("聊天模型名称、提供方、服务地址和模型名称不能为空");
        }
        if (!PROVIDERS.contains(request.getChatProvider().trim().toLowerCase(Locale.ROOT))) {
            throw new BizException("聊天模型提供方仅支持 ollama、openai 或 vllm");
        }
        try {
            URI uri = URI.create(request.getChatBaseUrl().trim());
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new BizException("聊天模型地址必须是有效的 HTTP 或 HTTPS URL");
        }
        if (request.getContextLength() != null
                && (request.getContextLength() < MIN_CONTEXT_LENGTH
                || request.getContextLength() > MAX_CONTEXT_LENGTH)) {
            throw new BizException("上下文长度必须在 1024 到 2000000 Token 之间");
        }
        if (request.getName().trim().length() > 128
                || request.getChatModel().trim().length() > 128
                || request.getChatBaseUrl().trim().length() > 512
                || (request.getApiKey() != null && request.getApiKey().length() > 1024)) {
            throw new BizException("聊天模型字段长度超出限制");
        }
    }

    private static AiRagChatModel copy(AiRagChatModel source) {
        AiRagChatModel copy = new AiRagChatModel();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setChatProvider(source.getChatProvider());
        copy.setChatBaseUrl(source.getChatBaseUrl());
        copy.setChatModel(source.getChatModel());
        copy.setContextLength(source.getContextLength());
        copy.setApiKey(source.getApiKey());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    private static ChatModelResponse toResponse(AiRagChatModel model) {
        ChatModelResponse response = new ChatModelResponse();
        response.setId(model.getId());
        response.setName(model.getName());
        response.setChatProvider(model.getChatProvider());
        response.setChatBaseUrl(model.getChatBaseUrl());
        response.setChatModel(model.getChatModel());
        response.setContextLength(model.getContextLength() == null
                ? DEFAULT_CONTEXT_LENGTH : model.getContextLength());
        response.setApiKeyConfigured(StringUtils.hasText(model.getApiKey()));
        return response;
    }

    private static long safeCurrent(long current) {
        return Math.max(1, current);
    }

    private static long safeSize(long size) {
        return Math.min(100, Math.max(1, size));
    }
}
