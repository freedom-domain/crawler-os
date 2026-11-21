package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiAssistant;
import com.collect.ai.mapper.AiAssistantMapper;
import com.collect.ai.mapper.AiRagChatModelMapper;
import com.collect.ai.mapper.AiRagVectorModelMapper;
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
    private final AiRagChatModelMapper chatModelMapper;
    private final AiRagVectorModelMapper vectorModelMapper;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;

    public List<AiAssistantDtos.Response> list() {
        return assistantMapper.selectList(new LambdaQueryWrapper<AiAssistant>()
                        .orderByAsc(AiAssistant::getSort)
                        .orderByDesc(AiAssistant::getId))
                .stream().map(this::toResponse).toList();
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
        if (assistant == null) throw new BizException("AI 助手不存在");
        return toResponse(assistant);
    }

    @Transactional
    public AiAssistantDtos.Response create(AiAssistantDtos.Request request) {
        validate(request);
        AiAssistant assistant = fromRequest(new AiAssistant(), request);
        LocalDateTime now = LocalDateTime.now();
        assistant.setCreateTime(now);
        assistant.setUpdateTime(now);
        if (assistantMapper.insert(assistant) != 1) throw new IllegalStateException("创建 AI 助手失败");
        return toResponse(assistant);
    }

    @Transactional
    public AiAssistantDtos.Response update(Long id, AiAssistantDtos.Request request) {
        validate(request);
        AiAssistant assistant = assistantMapper.selectById(id);
        if (assistant == null) throw new BizException("AI 助手不存在");
        fromRequest(assistant, request);
        assistant.setUpdateTime(LocalDateTime.now());
        if (assistantMapper.updateById(assistant) != 1) throw new IllegalStateException("更新 AI 助手失败");
        return toResponse(assistant);
    }

    @Transactional
    public void delete(Long id) {
        AiAssistant assistant = assistantMapper.selectById(id);
        if (assistant == null) throw new BizException("AI 助手不存在");
        if (assistantMapper.deleteById(id) != 1) throw new IllegalStateException("删除 AI 助手失败");
    }

    private void validate(AiAssistantDtos.Request request) {
        if (request == null || !StringUtils.hasText(request.getName()))
            throw new BizException("助手名称不能为空");
        if (request.getName().trim().length() > 64) throw new BizException("助手名称不能超过 64 个字符");
        if (request.getChatModelId() == null || chatModelMapper.selectById(request.getChatModelId()) == null)
            throw new BizException("请选择有效的聊天模型");
        if (request.getVectorModelId() == null || vectorModelMapper.selectById(request.getVectorModelId()) == null)
            throw new BizException("请选择有效的向量模型");
        if (request.getKnowledgeBaseId() != null
                && knowledgeBaseMapper.selectById(request.getKnowledgeBaseId()) == null)
            throw new BizException("请选择有效的知识库");
    }

    private AiAssistant fromRequest(AiAssistant assistant, AiAssistantDtos.Request request) {
        assistant.setName(request.getName().trim());
        assistant.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        assistant.setAvatar(request.getAvatar() == null ? "" : request.getAvatar().trim());
        assistant.setSystemPrompt(request.getSystemPrompt() == null ? "" : request.getSystemPrompt().trim());
        assistant.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        assistant.setChatModelId(request.getChatModelId());
        assistant.setVectorModelId(request.getVectorModelId());
        assistant.setKnowledgeBaseId(request.getKnowledgeBaseId());
        assistant.setSort(request.getSort() == null ? 0 : request.getSort());
        return assistant;
    }

    private AiAssistantDtos.Response toResponse(AiAssistant assistant) {
        AiAssistantDtos.Response response = new AiAssistantDtos.Response();
        response.setId(assistant.getId());
        response.setName(assistant.getName());
        response.setDescription(assistant.getDescription());
        response.setAvatar(assistant.getAvatar());
        response.setSystemPrompt(assistant.getSystemPrompt());
        response.setEnabled(assistant.getEnabled());
        response.setChatModelId(assistant.getChatModelId());
        response.setVectorModelId(assistant.getVectorModelId());
        response.setKnowledgeBaseId(assistant.getKnowledgeBaseId());
        var chatModel = chatModelMapper.selectById(assistant.getChatModelId());
        var vectorModel = vectorModelMapper.selectById(assistant.getVectorModelId());
        var knowledgeBase = assistant.getKnowledgeBaseId() == null
                ? null : knowledgeBaseMapper.selectById(assistant.getKnowledgeBaseId());
        response.setChatModelName(chatModel == null ? "-" : chatModel.getName());
        response.setVectorModelName(vectorModel == null ? "-" : vectorModel.getName());
        response.setKnowledgeBaseName(knowledgeBase == null ? "-" : knowledgeBase.getName());
        response.setSort(assistant.getSort());
        response.setCreateTime(assistant.getCreateTime());
        response.setUpdateTime(assistant.getUpdateTime());
        return response;
    }
}
