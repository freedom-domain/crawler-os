package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.ai.entity.AiRagRerankModel;
import com.collect.ai.mapper.AiRagRerankModelMapper;
import com.collect.ai.entity.AiKnowledgeBase;
import com.collect.ai.mapper.AiKnowledgeBaseMapper;
import com.collect.ai.rag.dto.RerankModelRequest;
import com.collect.ai.rag.dto.RerankModelResponse;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RerankModelService {

    private final AiRagRerankModelMapper rerankModelMapper;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;

    public List<RerankModelResponse> list() {
        return rerankModelMapper.selectList(
                        new LambdaQueryWrapper<AiRagRerankModel>()
                                .orderByAsc(AiRagRerankModel::getId))
                .stream().map(RerankModelService::toResponse).toList();
    }

    public IPage<RerankModelResponse> page(long current, long size, String keyword,
                                           String provider, String apiKeyStatus) {
        LambdaQueryWrapper<AiRagRerankModel> query = new LambdaQueryWrapper<AiRagRerankModel>()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(AiRagRerankModel::getName, keyword.trim())
                        .or().like(AiRagRerankModel::getRerankModel, keyword.trim())
                        .or().like(AiRagRerankModel::getRerankBaseUrl, keyword.trim()))
                .eq(StringUtils.hasText(provider), AiRagRerankModel::getRerankProvider, provider)
                .orderByAsc(AiRagRerankModel::getId);
        if ("configured".equals(apiKeyStatus)) {
            query.isNotNull(AiRagRerankModel::getApiKey).ne(AiRagRerankModel::getApiKey, "");
        } else if ("unconfigured".equals(apiKeyStatus)) {
            query.and(wrapper -> wrapper.isNull(AiRagRerankModel::getApiKey)
                    .or().eq(AiRagRerankModel::getApiKey, ""));
        }
        return rerankModelMapper.selectPage(new Page<>(safeCurrent(current), safeSize(size)), query)
                .convert(RerankModelService::toResponse);
    }

    public String getApiKey(Long id) {
        return requireModel(id).getApiKey();
    }

    @Transactional
    public void updateApiKey(Long id, String apiKey) {
        validateApiKey(apiKey);
        AiRagRerankModel model = requireModel(id);
        model.setApiKey(apiKey == null ? "" : apiKey.trim());
        model.setUpdateTime(LocalDateTime.now());
        if (rerankModelMapper.updateById(model) != 1) {
            throw new IllegalStateException("保存重排模型 API Key 失败");
        }
    }

    @Transactional
    public RerankModelResponse create(RerankModelRequest request) {
        validate(request);
        ensureUniqueName(request.getName(), null);
        AiRagRerankModel model = fromRequest(request);
        model.setApiKey(request.getApiKey() == null ? "" : request.getApiKey().trim());
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(model.getCreateTime());
        if (rerankModelMapper.insert(model) != 1) {
            throw new IllegalStateException("创建重排模型失败");
        }
        return toResponse(model);
    }

    @Transactional
    public RerankModelResponse update(Long id, RerankModelRequest request) {
        validate(request);
        AiRagRerankModel model = requireModel(id);
        ensureUniqueName(request.getName(), id);
        model.setName(request.getName().trim());
        model.setRerankProvider(request.getRerankProvider().trim());
        model.setRerankBaseUrl(request.getRerankBaseUrl().trim());
        model.setRerankModel(request.getRerankModel().trim());
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            model.setApiKey("");
        } else if (StringUtils.hasText(request.getApiKey())) {
            model.setApiKey(request.getApiKey().trim());
        }
        model.setUpdateTime(LocalDateTime.now());
        if (rerankModelMapper.updateById(model) != 1) {
            throw new IllegalStateException("保存重排模型失败");
        }
        return toResponse(model);
    }

    @Transactional
    public void delete(Long id) {
        requireModel(id);
        if (knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getRerankModelId, id)) > 0) {
            throw new BizException("该重排模型正被知识库引用，不能删除");
        }
        if (rerankModelMapper.deleteById(id) != 1) {
            throw new IllegalStateException("删除重排模型失败");
        }
    }

    private AiRagRerankModel requireModel(Long id) {
        AiRagRerankModel model = rerankModelMapper.selectById(id);
        if (model == null) {
            throw new BizException("重排模型不存在");
        }
        return model;
    }

    private void ensureUniqueName(String name, Long excludedId) {
        LambdaQueryWrapper<AiRagRerankModel> query = new LambdaQueryWrapper<AiRagRerankModel>()
                .eq(AiRagRerankModel::getName, name.trim());
        if (excludedId != null) {
            query.ne(AiRagRerankModel::getId, excludedId);
        }
        if (rerankModelMapper.selectCount(query) > 0) {
            throw new BizException("重排模型配置名称已存在");
        }
    }

    private static AiRagRerankModel fromRequest(RerankModelRequest request) {
        AiRagRerankModel model = new AiRagRerankModel();
        model.setName(request.getName().trim());
        model.setRerankProvider(request.getRerankProvider().trim());
        model.setRerankBaseUrl(request.getRerankBaseUrl().trim());
        model.setRerankModel(request.getRerankModel().trim());
        return model;
    }

    private static void validate(RerankModelRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getRerankProvider())
                || !StringUtils.hasText(request.getRerankBaseUrl())
                || !StringUtils.hasText(request.getRerankModel())) {
            throw new BizException("重排模型名称、提供方、服务地址和模型名称不能为空");
        }
        try {
            URI uri = URI.create(request.getRerankBaseUrl().trim());
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new BizException("重排模型地址必须是有效的 HTTP 或 HTTPS URL");
        }
        if (request.getName().trim().length() > 128
                || request.getRerankProvider().trim().length() > 32
                || request.getRerankModel().trim().length() > 128
                || request.getRerankBaseUrl().trim().length() > 512) {
            throw new BizException("重排模型字段长度超出限制");
        }
        validateApiKey(request.getApiKey());
    }

    private static void validateApiKey(String apiKey) {
        if (apiKey != null && apiKey.length() > 1024) {
            throw new BizException("API Key 长度不能超过 1024 个字符");
        }
    }

    private static RerankModelResponse toResponse(AiRagRerankModel model) {
        RerankModelResponse response = new RerankModelResponse();
        response.setId(model.getId());
        response.setName(model.getName());
        response.setRerankProvider(model.getRerankProvider());
        response.setRerankBaseUrl(model.getRerankBaseUrl());
        response.setRerankModel(model.getRerankModel());
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
