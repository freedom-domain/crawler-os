package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiRagConfig;
import com.collect.ai.entity.AiRagVectorModel;
import com.collect.ai.mapper.AiRagConfigMapper;
import com.collect.ai.mapper.AiRagVectorModelMapper;
import com.collect.ai.rag.dto.VectorModelRequest;
import com.collect.ai.rag.dto.VectorModelResponse;
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
public class VectorModelService {

    private static final Set<String> PROVIDERS = Set.of("ollama", "openai");

    private final AiRagVectorModelMapper vectorModelMapper;
    private final AiRagConfigMapper configMapper;
    private final RagConfigService ragConfigService;

    public List<VectorModelResponse> list() {
        return vectorModelMapper.selectList(
                        new LambdaQueryWrapper<AiRagVectorModel>()
                                .orderByAsc(AiRagVectorModel::getId))
                .stream().map(VectorModelService::toResponse).toList();
    }

    @Transactional
    public VectorModelResponse create(VectorModelRequest request) {
        validate(request);
        ensureUniqueName(request.getName(), null);
        AiRagVectorModel model = fromRequest(request);
        model.setApiKey(StringUtils.hasText(request.getApiKey()) ? request.getApiKey().trim() : "");
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(model.getCreateTime());
        if (vectorModelMapper.insert(model) != 1) {
            throw new IllegalStateException("创建向量模型失败");
        }
        return toResponse(model);
    }

    @Transactional
    public VectorModelResponse update(Long id, VectorModelRequest request) {
        validate(request);
        AiRagVectorModel model = requireModel(id);
        ensureUniqueName(request.getName(), id);
        AiRagVectorModel previous = copy(model);
        copyRequest(request, model);
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            model.setApiKey("");
        } else if (StringUtils.hasText(request.getApiKey())) {
            model.setApiKey(request.getApiKey().trim());
        }
        model.setUpdateTime(LocalDateTime.now());
        if (vectorModelMapper.updateById(model) != 1) {
            throw new IllegalStateException("保存向量模型失败");
        }
        try {
            ragConfigService.refreshSelectedVectorModel(id);
        } catch (RuntimeException e) {
            vectorModelMapper.updateById(previous);
            throw e;
        }
        return toResponse(model);
    }

    @Transactional
    public void delete(Long id) {
        requireModel(id);
        Long references = configMapper.selectCount(new LambdaQueryWrapper<AiRagConfig>()
                .eq(AiRagConfig::getVectorModelId, id));
        if (references > 0) {
            throw new BizException("该向量模型正被 AI 配置引用，不能删除");
        }
        if (vectorModelMapper.deleteById(id) != 1) {
            throw new IllegalStateException("删除向量模型失败");
        }
    }

    private AiRagVectorModel requireModel(Long id) {
        AiRagVectorModel model = vectorModelMapper.selectById(id);
        if (model == null) {
            throw new BizException("向量模型不存在");
        }
        return model;
    }

    private void ensureUniqueName(String name, Long excludedId) {
        LambdaQueryWrapper<AiRagVectorModel> query = new LambdaQueryWrapper<AiRagVectorModel>()
                .eq(AiRagVectorModel::getName, name.trim());
        if (excludedId != null) {
            query.ne(AiRagVectorModel::getId, excludedId);
        }
        if (vectorModelMapper.selectCount(query) > 0) {
            throw new BizException("向量模型配置名称已存在");
        }
    }

    private static AiRagVectorModel fromRequest(VectorModelRequest request) {
        AiRagVectorModel model = new AiRagVectorModel();
        copyRequest(request, model);
        return model;
    }

    private static void copyRequest(VectorModelRequest request, AiRagVectorModel model) {
        model.setName(request.getName().trim());
        model.setEmbeddingProvider(request.getEmbeddingProvider().trim().toLowerCase(Locale.ROOT));
        model.setEmbeddingBaseUrl(request.getEmbeddingBaseUrl().trim());
        model.setEmbeddingModel(request.getEmbeddingModel().trim());
        model.setDimensions(request.getDimensions());
        model.setTopK(request.getTopK());
        model.setChunkSize(request.getChunkSize());
        model.setChunkOverlap(request.getChunkOverlap());
    }

    private static void validate(VectorModelRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getEmbeddingProvider())
                || !StringUtils.hasText(request.getEmbeddingBaseUrl())
                || !StringUtils.hasText(request.getEmbeddingModel())) {
            throw new BizException("向量模型名称、提供方、服务地址和模型名称不能为空");
        }
        if (!PROVIDERS.contains(request.getEmbeddingProvider().trim().toLowerCase(Locale.ROOT))) {
            throw new BizException("向量模型提供方仅支持 ollama 或 openai");
        }
        try {
            URI uri = URI.create(request.getEmbeddingBaseUrl().trim());
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new BizException("Embedding 地址必须是有效的 HTTP 或 HTTPS URL");
        }
        if (request.getName().trim().length() > 128
                || request.getEmbeddingModel().trim().length() > 128
                || request.getEmbeddingBaseUrl().trim().length() > 512
                || (request.getApiKey() != null && request.getApiKey().length() > 1024)) {
            throw new BizException("向量模型字段长度超出限制");
        }
        if (request.getDimensions() == null || request.getDimensions() < 1
                || request.getDimensions() > 65536
                || request.getTopK() == null || request.getTopK() < 1 || request.getTopK() > 20
                || request.getChunkSize() == null || request.getChunkSize() < 200
                || request.getChunkSize() > 20000
                || request.getChunkOverlap() == null || request.getChunkOverlap() < 0
                || request.getChunkOverlap() >= request.getChunkSize()) {
            throw new BizException("向量维度、Top K 或分块参数不合法");
        }
    }

    private static AiRagVectorModel copy(AiRagVectorModel source) {
        AiRagVectorModel copy = new AiRagVectorModel();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setEmbeddingProvider(source.getEmbeddingProvider());
        copy.setEmbeddingBaseUrl(source.getEmbeddingBaseUrl());
        copy.setEmbeddingModel(source.getEmbeddingModel());
        copy.setApiKey(source.getApiKey());
        copy.setDimensions(source.getDimensions());
        copy.setTopK(source.getTopK());
        copy.setChunkSize(source.getChunkSize());
        copy.setChunkOverlap(source.getChunkOverlap());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    private static VectorModelResponse toResponse(AiRagVectorModel model) {
        VectorModelResponse response = new VectorModelResponse();
        response.setId(model.getId());
        response.setName(model.getName());
        response.setEmbeddingProvider(model.getEmbeddingProvider());
        response.setEmbeddingBaseUrl(model.getEmbeddingBaseUrl());
        response.setEmbeddingModel(model.getEmbeddingModel());
        response.setApiKeyConfigured(StringUtils.hasText(model.getApiKey()));
        response.setDimensions(model.getDimensions());
        response.setTopK(model.getTopK());
        response.setChunkSize(model.getChunkSize());
        response.setChunkOverlap(model.getChunkOverlap());
        return response;
    }
}
