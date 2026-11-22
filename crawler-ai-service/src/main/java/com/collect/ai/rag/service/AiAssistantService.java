package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.ai.entity.AiAssistant;
import com.collect.ai.entity.AiKnowledgeBase;
import com.collect.ai.entity.AiAssistantKnowledgeBase;
import com.collect.ai.mapper.AiAssistantMapper;
import com.collect.ai.mapper.AiAssistantKnowledgeBaseMapper;
import com.collect.ai.mapper.AiKnowledgeBaseMapper;
import com.collect.ai.rag.dto.AiAssistantDtos;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final AiAssistantMapper assistantMapper;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiAssistantKnowledgeBaseMapper assistantKnowledgeBaseMapper;

    public List<AiAssistantDtos.Response> list() {
        return assistantMapper.selectList(new LambdaQueryWrapper<AiAssistant>()
                        .orderByAsc(AiAssistant::getSort)
                        .orderByDesc(AiAssistant::getId))
                .stream().map(this::toResponse).toList();
    }

    public IPage<AiAssistantDtos.Response> page(long current, long size, String keyword, Boolean enabled) {
        LambdaQueryWrapper<AiAssistant> query = new LambdaQueryWrapper<AiAssistant>()
                .eq(enabled != null, AiAssistant::getEnabled, enabled)
                .orderByAsc(AiAssistant::getSort)
                .orderByDesc(AiAssistant::getId);
        if (StringUtils.hasText(keyword)) {
            String search = keyword.trim();
            List<Long> matchingBaseIds = knowledgeBaseMapper.selectList(
                            new LambdaQueryWrapper<AiKnowledgeBase>()
                                    .select(AiKnowledgeBase::getId)
                                    .like(AiKnowledgeBase::getName, search))
                    .stream().map(AiKnowledgeBase::getId).toList();
            List<Long> matchingAssistantIds = matchingBaseIds.isEmpty()
                    ? List.of()
                    : assistantKnowledgeBaseMapper.selectList(
                                    new LambdaQueryWrapper<AiAssistantKnowledgeBase>()
                                            .select(AiAssistantKnowledgeBase::getAssistantId)
                                            .in(AiAssistantKnowledgeBase::getKnowledgeBaseId, matchingBaseIds))
                            .stream().map(AiAssistantKnowledgeBase::getAssistantId).distinct().toList();
            query.and(wrapper -> {
                wrapper.like(AiAssistant::getName, search).or().like(AiAssistant::getDescription, search);
                if (!matchingAssistantIds.isEmpty()) {
                    wrapper.or().in(AiAssistant::getId, matchingAssistantIds);
                }
            });
        }
        return assistantMapper.selectPage(new Page<>(safeCurrent(current), safeSize(size)), query)
                .convert(this::toResponse);
    }

    public List<AiAssistantDtos.Response> listEnabled() {
        return assistantMapper.selectList(new LambdaQueryWrapper<AiAssistant>()
                        .eq(AiAssistant::getEnabled, true)
                        .orderByAsc(AiAssistant::getSort)
                        .orderByDesc(AiAssistant::getId))
                .stream().map(this::toResponse).toList();
    }

    public AiAssistantDtos.Response get(Long id) {
        AiAssistant assistant = assistantMapper.selectById(id);
        if (assistant == null) throw new BizException("智能体不存在");
        return toResponse(assistant);
    }

    public boolean hasKnowledgeBase(Long assistantId, Long knowledgeBaseId) {
        LambdaQueryWrapper<AiAssistantKnowledgeBase> query =
                new LambdaQueryWrapper<AiAssistantKnowledgeBase>()
                        .eq(AiAssistantKnowledgeBase::getAssistantId, assistantId);
        if (knowledgeBaseId != null) {
            query.eq(AiAssistantKnowledgeBase::getKnowledgeBaseId, knowledgeBaseId);
        }
        return assistantKnowledgeBaseMapper.selectCount(query) > 0;
    }

    @Transactional
    public AiAssistantDtos.Response create(AiAssistantDtos.Request request) {
        validate(request);
        AiAssistant assistant = fromRequest(new AiAssistant(), request);
        LocalDateTime now = LocalDateTime.now();
        assistant.setCreateTime(now);
        assistant.setUpdateTime(now);
        if (assistantMapper.insert(assistant) != 1) throw new IllegalStateException("创建智能体失败");
        syncKnowledgeBases(assistant.getId(), request.getKnowledgeBaseIds());
        return toResponse(assistant);
    }

    @Transactional
    public AiAssistantDtos.Response update(Long id, AiAssistantDtos.Request request) {
        validate(request);
        AiAssistant assistant = assistantMapper.selectById(id);
        if (assistant == null) throw new BizException("智能体不存在");
        fromRequest(assistant, request);
        assistant.setUpdateTime(LocalDateTime.now());
        if (assistantMapper.updateById(assistant) != 1) throw new IllegalStateException("更新智能体失败");
        syncKnowledgeBases(id, request.getKnowledgeBaseIds());
        return toResponse(assistant);
    }

    @Transactional
    public void delete(Long id) {
        AiAssistant assistant = assistantMapper.selectById(id);
        if (assistant == null) throw new BizException("智能体不存在");
        assistantKnowledgeBaseMapper.delete(new LambdaQueryWrapper<AiAssistantKnowledgeBase>()
                .eq(AiAssistantKnowledgeBase::getAssistantId, id));
        if (assistantMapper.deleteById(id) != 1) throw new IllegalStateException("删除智能体失败");
    }

    private void validate(AiAssistantDtos.Request request) {
        if (request == null || !StringUtils.hasText(request.getName()))
            throw new BizException("智能体名称不能为空");
        if (request.getName().trim().length() > 64) throw new BizException("智能体名称不能超过 64 个字符");
        if (request.getWelcomeMessage() != null && request.getWelcomeMessage().trim().length() > 256)
            throw new BizException("欢迎语不能超过 256 个字符");
        if (request.getKnowledgeBaseIds() != null) {
            for (Long knowledgeBaseId : request.getKnowledgeBaseIds()) {
                if (knowledgeBaseMapper.selectById(knowledgeBaseId) == null)
                    throw new BizException("请选择有效的知识库");
            }
        }
    }

    private AiAssistant fromRequest(AiAssistant assistant, AiAssistantDtos.Request request) {
        assistant.setName(request.getName().trim());
        assistant.setWelcomeMessage(request.getWelcomeMessage() == null ? "" : request.getWelcomeMessage().trim());
        assistant.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        assistant.setAvatar(request.getAvatar() == null ? "" : request.getAvatar().trim());
        assistant.setSystemPrompt(request.getSystemPrompt() == null ? "" : request.getSystemPrompt().trim());
        assistant.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        assistant.setSort(request.getSort() == null ? 0 : request.getSort());
        return assistant;
    }

    private void syncKnowledgeBases(Long assistantId, List<Long> knowledgeBaseIds) {
        assistantKnowledgeBaseMapper.delete(new LambdaQueryWrapper<AiAssistantKnowledgeBase>()
                .eq(AiAssistantKnowledgeBase::getAssistantId, assistantId));
        if (knowledgeBaseIds == null) return;
        for (Long knowledgeBaseId : knowledgeBaseIds.stream().distinct().toList()) {
            AiAssistantKnowledgeBase relation = new AiAssistantKnowledgeBase();
            relation.setAssistantId(assistantId);
            relation.setKnowledgeBaseId(knowledgeBaseId);
            if (assistantKnowledgeBaseMapper.insert(relation) != 1)
                throw new IllegalStateException("保存智能体知识库关联失败");
        }
    }

    private AiAssistantDtos.Response toResponse(AiAssistant assistant) {
        AiAssistantDtos.Response response = new AiAssistantDtos.Response();
        response.setId(assistant.getId());
        response.setName(assistant.getName());
        response.setWelcomeMessage(assistant.getWelcomeMessage());
        response.setDescription(assistant.getDescription());
        response.setAvatar(assistant.getAvatar());
        response.setSystemPrompt(assistant.getSystemPrompt());
        response.setEnabled(assistant.getEnabled());
        List<AiAssistantKnowledgeBase> relations = assistantKnowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<AiAssistantKnowledgeBase>()
                        .eq(AiAssistantKnowledgeBase::getAssistantId, assistant.getId()));
        List<Long> knowledgeBaseIds = relations.stream()
                .map(AiAssistantKnowledgeBase::getKnowledgeBaseId).distinct().toList();
        List<String> knowledgeBaseNames = knowledgeBaseIds.stream()
                .map(id -> {
                    var base = knowledgeBaseMapper.selectById(id);
                    return base == null ? "-" : base.getName();
                }).toList();
        response.setKnowledgeBaseIds(knowledgeBaseIds);
        response.setKnowledgeBaseNames(knowledgeBaseNames);
        response.setSort(assistant.getSort());
        response.setCreateTime(assistant.getCreateTime());
        response.setUpdateTime(assistant.getUpdateTime());
        return response;
    }

    private static long safeCurrent(long current) {
        return Math.max(1, current);
    }

    private static long safeSize(long size) {
        return Math.min(100, Math.max(1, size));
    }
}
