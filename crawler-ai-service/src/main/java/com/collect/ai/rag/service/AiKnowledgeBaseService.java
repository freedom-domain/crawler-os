package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiKnowledgeBase;
import com.collect.ai.entity.AiAssistantKnowledgeBase;
import com.collect.ai.mapper.AiKnowledgeBaseMapper;
import com.collect.ai.mapper.AiRagVectorModelMapper;
import com.collect.ai.mapper.AiAssistantKnowledgeBaseMapper;
import com.collect.ai.rag.dto.AiKnowledgeBaseDtos;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
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
    private final AiAssistantKnowledgeBaseMapper assistantKnowledgeBaseMapper;

    public List<AiKnowledgeBaseDtos.Response> list() {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<AiKnowledgeBase>()
                        .orderByAsc(AiKnowledgeBase::getSort)
                        .orderByAsc(AiKnowledgeBase::getId))
                .stream().map(this::toResponse).toList();
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
            throw new BizException("该知识库正被 AI 助手引用，不能删除");
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
        if (!"UPLOAD".equalsIgnoreCase(request.getSourceType())
                && !StringUtils.hasText(request.getSourceIndex()))
            throw new BizException("请输入源索引名称");
        if (!StringUtils.hasText(request.getVectorIndex()))
            throw new BizException("请输入向量索引名称");
        if (!StringUtils.hasText(request.getVectorField()))
            throw new BizException("请输入向量字段名称");
    }

    private AiKnowledgeBase fromRequest(AiKnowledgeBase base, AiKnowledgeBaseDtos.Request request) {
        base.setName(request.getName().trim());
        base.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        base.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        base.setVectorModelId(request.getVectorModelId());
        String sourceType = "UPLOAD".equalsIgnoreCase(request.getSourceType()) ? "UPLOAD" : "ES_INDEX";
        String sourceIndex = request.getSourceIndex();
        if ("UPLOAD".equals(sourceType) && !StringUtils.hasText(sourceIndex)) {
            sourceIndex = "spider_content";
        }
        base.setSourceIndex(normalizeIndex(sourceIndex));
        base.setVectorIndex(normalizeIndex(request.getVectorIndex()));
        base.setVectorField(normalizeIndex(request.getVectorField()));
        base.setSourceType(sourceType);
        base.setUploadBucket(StringUtils.hasText(request.getUploadBucket())
                ? request.getUploadBucket().trim().toLowerCase(Locale.ROOT)
                : "crawler-knowledge");
        base.setSort(request.getSort() == null ? 0 : request.getSort());
        return base;
    }

    private static String normalizeIndex(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_").replaceAll("^[_-]+|[_-]+$", "");
    }

    private AiKnowledgeBaseDtos.Response toResponse(AiKnowledgeBase base) {
        AiKnowledgeBaseDtos.Response response = new AiKnowledgeBaseDtos.Response();
        response.setId(base.getId());
        response.setName(base.getName());
        response.setDescription(base.getDescription());
        response.setEnabled(base.getEnabled());
        response.setVectorModelId(base.getVectorModelId());
        var model = vectorModelMapper.selectById(base.getVectorModelId());
        response.setVectorModelName(model == null ? "-" : model.getName());
        response.setSourceIndex(base.getSourceIndex());
        response.setVectorIndex(base.getVectorIndex());
        response.setVectorField(base.getVectorField());
        response.setSourceType(base.getSourceType());
        response.setUploadBucket(base.getUploadBucket());
        response.setSort(base.getSort());
        response.setCreateTime(base.getCreateTime());
        response.setUpdateTime(base.getUpdateTime());
        return response;
    }
}
