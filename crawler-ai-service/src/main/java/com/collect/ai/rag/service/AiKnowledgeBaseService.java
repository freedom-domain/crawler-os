package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.ai.entity.AiKnowledgeBase;
import com.collect.ai.entity.AiRagVectorModel;
import com.collect.ai.entity.AiRagRerankModel;
import com.collect.ai.entity.AiAssistantKnowledgeBase;
import com.collect.ai.mapper.AiKnowledgeBaseMapper;
import com.collect.ai.mapper.AiRagVectorModelMapper;
import com.collect.ai.mapper.AiRagRerankModelMapper;
import com.collect.ai.mapper.AiAssistantKnowledgeBaseMapper;
import com.collect.ai.rag.dto.AiKnowledgeBaseDtos;
import com.collect.ai.rag.config.RagProperties;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiKnowledgeBaseService {

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiRagVectorModelMapper vectorModelMapper;
    private final AiRagRerankModelMapper rerankModelMapper;
    private final AiAssistantKnowledgeBaseMapper assistantKnowledgeBaseMapper;
    private final RagProperties ragProperties;

    @Value("${minio.bucket:crawler-knowledge}")
    private String uploadBucket;

    public List<AiKnowledgeBaseDtos.Response> list() {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<AiKnowledgeBase>()
                        .orderByAsc(AiKnowledgeBase::getSort)
                        .orderByAsc(AiKnowledgeBase::getId))
                .stream().map(this::toResponse).toList();
    }

    public IPage<AiKnowledgeBaseDtos.Response> page(long current, long size, String keyword,
                                                    String sourceType, Boolean enabled) {
        LambdaQueryWrapper<AiKnowledgeBase> query = new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(StringUtils.hasText(sourceType), AiKnowledgeBase::getSourceType, sourceType)
                .eq(enabled != null, AiKnowledgeBase::getEnabled, enabled)
                .orderByAsc(AiKnowledgeBase::getSort)
                .orderByAsc(AiKnowledgeBase::getId);
        if (StringUtils.hasText(keyword)) {
            String search = keyword.trim();
            List<Long> modelIds = vectorModelMapper.selectList(
                            new LambdaQueryWrapper<AiRagVectorModel>()
                                    .select(AiRagVectorModel::getId)
                                    .like(AiRagVectorModel::getName, search))
                    .stream().map(AiRagVectorModel::getId).toList();
            query.and(wrapper -> {
                wrapper.like(AiKnowledgeBase::getName, search)
                        .or().like(AiKnowledgeBase::getDescription, search)
                        .or().like(AiKnowledgeBase::getSourceIndex, search)
                        .or().like(AiKnowledgeBase::getVectorIndex, search)
                        .or().like(AiKnowledgeBase::getVectorField, search);
                if (!modelIds.isEmpty()) {
                    wrapper.or().in(AiKnowledgeBase::getVectorModelId, modelIds);
                }
            });
        }
        return knowledgeBaseMapper.selectPage(new Page<>(safeCurrent(current), safeSize(size)), query)
                .convert(this::toResponse);
    }

    public List<AiKnowledgeBaseDtos.Response> listEnabled() {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<AiKnowledgeBase>()
                        .eq(AiKnowledgeBase::getEnabled, true)
                        .orderByAsc(AiKnowledgeBase::getSort)
                        .orderByAsc(AiKnowledgeBase::getId))
                .stream().map(this::toResponse).toList();
    }

    public AiKnowledgeBaseDtos.Response get(Long id) {
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(id);
        if (base == null) throw new BizException("知识库不存在");
        return toResponse(base);
    }

    @Transactional
    public AiKnowledgeBaseDtos.Response create(AiKnowledgeBaseDtos.Request request) {
        validate(request);
        AiKnowledgeBase base = fromRequest(new AiKnowledgeBase(), request);
        LocalDateTime now = LocalDateTime.now();
        base.setCreateTime(now);
        base.setUpdateTime(now);
        if (knowledgeBaseMapper.insert(base) != 1) throw new IllegalStateException("创建知识库失败");
        return toResponse(base);
    }

    @Transactional
    public AiKnowledgeBaseDtos.Response update(Long id, AiKnowledgeBaseDtos.Request request) {
        validate(request);
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(id);
        if (base == null) throw new BizException("知识库不存在");
        fromRequest(base, request);
        base.setUpdateTime(LocalDateTime.now());
        if (knowledgeBaseMapper.updateById(base) != 1) throw new IllegalStateException("更新知识库失败");
        return toResponse(base);
    }

    @Transactional
    public void delete(Long id) {
        if (knowledgeBaseMapper.selectById(id) == null) throw new BizException("知识库不存在");
        if (assistantKnowledgeBaseMapper.selectCount(new LambdaQueryWrapper<AiAssistantKnowledgeBase>()
                .eq(AiAssistantKnowledgeBase::getKnowledgeBaseId, id)) > 0) {
            throw new BizException("该知识库正被智能体引用，不能删除");
        }
        if (knowledgeBaseMapper.deleteById(id) != 1) throw new IllegalStateException("删除知识库失败");
    }

    private void validate(AiKnowledgeBaseDtos.Request request) {
        if (request == null || !StringUtils.hasText(request.getName()))
            throw new BizException("知识库名称不能为空");
        if (request.getName().trim().length() > 64)
            throw new BizException("知识库名称不能超过 64 个字符");
        if (request.getVectorModelId() == null || vectorModelMapper.selectById(request.getVectorModelId()) == null)
            throw new BizException("请选择有效的向量模型");
        if (request.getRerankModelId() != null
                && rerankModelMapper.selectById(request.getRerankModelId()) == null)
            throw new BizException("请选择有效的重排模型");
    }

    private AiKnowledgeBase fromRequest(AiKnowledgeBase base, AiKnowledgeBaseDtos.Request request) {
        base.setName(request.getName().trim());
        base.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        base.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        base.setVectorModelId(request.getVectorModelId());
        base.setRerankModelId(request.getRerankModelId());
        String sourceType = "UPLOAD".equalsIgnoreCase(request.getSourceType()) ? "UPLOAD" : "ES_INDEX";
        base.setSourceIndex(normalizeIndex(ragProperties.resolveSourceIndex()));
        AiRagVectorModel model = vectorModelMapper.selectById(request.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        String normalizedVectorIndex = normalizeIndex(
                ragProperties.resolveVectorIndex(model.getEmbeddingModel()));
        if (!StringUtils.hasText(normalizedVectorIndex)) {
            throw new BizException("向量索引不能为空");
        }
        base.setVectorIndex(normalizedVectorIndex);
        base.setVectorField(normalizeIndex(ragProperties.resolveVectorField()));
        base.setSourceType(sourceType);
        base.setUploadBucket(normalizeBucket(uploadBucket));
        base.setSort(request.getSort() == null ? 0 : request.getSort());
        return base;
    }

    private static String normalizeIndex(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_").replaceAll("^[_-]+|[_-]+$", "");
    }

    private static String normalizeBucket(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.-]", "-").replaceAll("^[-.]+|[-.]+$", "");
        return StringUtils.hasText(normalized) ? normalized : "crawler-knowledge";
    }

    private AiKnowledgeBaseDtos.Response toResponse(AiKnowledgeBase base) {
        AiKnowledgeBaseDtos.Response response = new AiKnowledgeBaseDtos.Response();
        response.setId(base.getId());
        response.setName(base.getName());
        response.setDescription(base.getDescription());
        response.setEnabled(base.getEnabled());
        response.setVectorModelId(base.getVectorModelId());
        var model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        response.setVectorModelName(model.getName());
        response.setRerankModelId(base.getRerankModelId());
        var rerankModel = base.getRerankModelId() == null
                ? null
                : rerankModelMapper.selectById(base.getRerankModelId());
        response.setRerankModelName(rerankModel == null ? "-" : rerankModel.getName());
        response.setSourceIndex(ragProperties.resolveSourceIndex());
        response.setVectorIndex(ragProperties.resolveVectorIndex(model.getEmbeddingModel()));
        response.setVectorField(ragProperties.resolveVectorField());
        response.setSourceType(base.getSourceType());
        response.setUploadBucket(base.getUploadBucket());
        response.setSort(base.getSort());
        response.setCreateTime(base.getCreateTime());
        response.setUpdateTime(base.getUpdateTime());
        return response;
    }

    private static long safeCurrent(long current) {
        return Math.max(1, current);
    }

    private static long safeSize(long size) {
        return Math.min(100, Math.max(1, size));
    }
}
