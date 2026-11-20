package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiChatConversation;
import com.collect.ai.entity.AiChatMessage;
import com.collect.ai.mapper.AiChatConversationMapper;
import com.collect.ai.mapper.AiChatMessageMapper;
import com.collect.ai.rag.config.RagProperties;
import com.collect.ai.rag.dto.AiChatDtos;
import com.collect.ai.rag.dto.RagDtos;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatHistoryService {

    private static final String DEFAULT_TITLE = "新对话";

    private final AiChatConversationMapper conversationMapper;
    private final AiChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public List<AiChatDtos.ConversationResponse> listConversations(Long userId) {
        return conversationMapper.selectRecentByUserId(userId).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    public int getContextLength() {
        return ragProperties.getRag().getContextLength();
    }

    public AiChatDtos.ConversationResponse createConversation(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        AiChatConversation conversation = new AiChatConversation();
        conversation.setUserId(userId);
        conversation.setTitle(DEFAULT_TITLE);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return toConversationResponse(conversation);
    }

    public List<AiChatDtos.MessageResponse> listMessages(Long userId, Long conversationId) {
        requireOwnedConversation(userId, conversationId);
        return messageMapper.selectByConversationId(conversationId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    public List<RagDtos.ChatHistoryMessage> getSuccessfulContext(Long userId, Long conversationId) {
        requireOwnedConversation(userId, conversationId);
        List<AiChatMessage> messages =
                messageMapper.selectSuccessfulContextByConversationId(conversationId);
        List<RagDtos.ChatHistoryMessage> context = new ArrayList<>(messages.size());
        for (AiChatMessage message : messages) {
            String content = message.getContent() == null ? "" : message.getContent();
            addContextMessage(context, message, content);
        }
        return context;
    }

    private static void addContextMessage(
            List<RagDtos.ChatHistoryMessage> context, AiChatMessage message, String content) {
        RagDtos.ChatHistoryMessage item = new RagDtos.ChatHistoryMessage();
        item.setRole(message.getRole());
        item.setContent(content);
        context.add(item);
    }

    public void deleteConversation(Long userId, Long conversationId) {
        int deleted = conversationMapper.delete(new LambdaQueryWrapper<AiChatConversation>()
                .eq(AiChatConversation::getId, conversationId)
                .eq(AiChatConversation::getUserId, userId));
        if (deleted == 0) {
            throw new BizException("未找到对话");
        }
    }

    public void requireOwnedConversation(Long userId, Long conversationId) {
        findOwnedConversation(userId, conversationId);
    }

    @Transactional
    public void clearMessages(Long userId, Long conversationId) {
        AiChatConversation conversation = findOwnedConversation(userId, conversationId);
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId));
        conversation.setTitle(DEFAULT_TITLE);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    @Transactional
    public void saveUserQuestion(Long userId, Long conversationId, String question) {
        AiChatConversation conversation = findOwnedConversation(userId, conversationId);
        LocalDateTime now = LocalDateTime.now();

        AiChatMessage userMessage = new AiChatMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(question);
        userMessage.setStatus("SUCCESS");
        userMessage.setSourcesJson("[]");
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            String title = question.length() > 80 ? question.substring(0, 80) : question;
            conversation.setTitle(title);
        }
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
    }

    @Transactional
    public AiChatDtos.MessageResponse beginAssistantStream(Long userId, Long conversationId) {
        findOwnedConversation(userId, conversationId);
        AiChatMessage message = new AiChatMessage();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setContent("");
        message.setReasoningContent("");
        message.setStatus("STREAMING");
        message.setSourcesJson("[]");
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return toMessageResponse(message);
    }

    public void appendAssistantStream(Long messageId, String delta) {
        if (delta == null || delta.isEmpty()) return;
        if (messageMapper.appendStreamingContent(messageId, delta) == 0) {
            throw new IllegalStateException("聊天流已结束或消息已删除");
        }
    }

    public void appendAssistantReasoning(Long messageId, String delta) {
        if (delta == null || delta.isEmpty()) return;
        if (messageMapper.appendStreamingReasoning(messageId, delta) == 0) {
            throw new IllegalStateException("聊天流已结束或消息已删除");
        }
    }

    @Transactional
    public AiChatDtos.MessageResponse cancelAssistantStream(
            Long userId, Long conversationId, Long messageId) {
        findOwnedConversation(userId, conversationId);
        findAssistantMessage(conversationId, messageId);
        messageMapper.cancelStreamingMessage(conversationId, messageId);
        return toMessageResponse(findAssistantMessage(conversationId, messageId));
    }

    @Transactional
    public AiChatDtos.MessageResponse completeAssistantStream(
            Long userId,
            Long conversationId,
            Long messageId,
            RagDtos.QueryResponse answer,
            String content,
            long thinkingDurationMs,
            long generationDurationMs) {
        findOwnedConversation(userId, conversationId);
        AiChatMessage message = findAssistantMessage(conversationId, messageId);
        if (!"STREAMING".equals(message.getStatus())) {
            throw new IllegalStateException("聊天流已结束");
        }
        message.setContent(content == null ? "" : content);
        message.setThinkingDurationMs(thinkingDurationMs);
        message.setGenerationDurationMs(generationDurationMs);
        message.setDurationMs(thinkingDurationMs + generationDurationMs);
        message.setStatus("SUCCESS");
        message.setSourcesJson(writeSources(answer.getSources()));
        messageMapper.updateById(message);
        return toMessageResponse(message);
    }

    @Transactional
    public AiChatDtos.MessageResponse failAssistantStream(
            Long userId, Long conversationId, Long messageId) {
        findOwnedConversation(userId, conversationId);
        AiChatMessage message = findAssistantMessage(conversationId, messageId);
        if (!"STREAMING".equals(message.getStatus())) {
            return toMessageResponse(message);
        }
        if (message.getContent() == null || message.getContent().isBlank()) {
            message.setContent("回答生成失败，请稍后重试。");
        }
        message.setStatus("FAILED");
        message.setSourcesJson("[]");
        messageMapper.updateById(message);
        return toMessageResponse(message);
    }

    public AiChatDtos.MessageResponse getAssistantStream(
            Long userId, Long conversationId, Long messageId) {
        findOwnedConversation(userId, conversationId);
        return toMessageResponse(findAssistantMessage(conversationId, messageId));
    }

    @Transactional
    public AiChatDtos.MessageResponse saveAssistantAnswer(
            Long userId,
            Long conversationId,
            RagDtos.QueryResponse answer,
            long durationMs) {
        findOwnedConversation(userId, conversationId);
        LocalDateTime now = LocalDateTime.now();

        AiChatMessage assistantMessage = new AiChatMessage();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer.getAnswer() == null ? "" : answer.getAnswer());
        assistantMessage.setDurationMs(durationMs);
        assistantMessage.setThinkingDurationMs(0L);
        assistantMessage.setGenerationDurationMs(durationMs);
        assistantMessage.setStatus("SUCCESS");
        assistantMessage.setSourcesJson(writeSources(answer.getSources()));
        assistantMessage.setCreatedAt(now);
        messageMapper.insert(assistantMessage);

        return toMessageResponse(assistantMessage);
    }

    @Transactional
    public AiChatDtos.MessageResponse saveAssistantFailure(Long userId, Long conversationId) {
        findOwnedConversation(userId, conversationId);
        AiChatMessage assistantMessage = new AiChatMessage();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("回答生成失败，请稍后重试。");
        assistantMessage.setStatus("FAILED");
        assistantMessage.setSourcesJson("[]");
        assistantMessage.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistantMessage);
        return toMessageResponse(assistantMessage);
    }

    private AiChatConversation findOwnedConversation(Long userId, Long conversationId) {
        AiChatConversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiChatConversation>()
                        .eq(AiChatConversation::getId, conversationId)
                        .eq(AiChatConversation::getUserId, userId));
        if (conversation == null) {
            throw new BizException("未找到对话");
        }
        return conversation;
    }

    private AiChatMessage findAssistantMessage(Long conversationId, Long messageId) {
        AiChatMessage message = messageMapper.selectOne(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getId, messageId)
                        .eq(AiChatMessage::getConversationId, conversationId)
                        .eq(AiChatMessage::getRole, "assistant"));
        if (message == null) {
            throw new BizException("未找到聊天回答");
        }
        return message;
    }

    private AiChatDtos.ConversationResponse toConversationResponse(AiChatConversation conversation) {
        AiChatDtos.ConversationResponse response = new AiChatDtos.ConversationResponse();
        response.setId(conversation.getId());
        response.setTitle(conversation.getTitle());
        response.setCreatedAt(conversation.getCreatedAt());
        response.setUpdatedAt(conversation.getUpdatedAt());
        return response;
    }

    private AiChatDtos.MessageResponse toMessageResponse(AiChatMessage message) {
        AiChatDtos.MessageResponse response = new AiChatDtos.MessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setReasoningContent(message.getReasoningContent() == null ? "" : message.getReasoningContent());
        response.setDurationMs(message.getDurationMs());
        response.setThinkingDurationMs(message.getThinkingDurationMs());
        response.setGenerationDurationMs(message.getGenerationDurationMs());
        response.setStatus(message.getStatus());
        response.setSources(readSources(message.getSourcesJson()));
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    private String writeSources(List<RagDtos.SourceReference> sources) {
        try {
            return objectMapper.writeValueAsString(sources == null ? List.of() : sources);
        } catch (Exception e) {
            throw new IllegalStateException("保存聊天来源失败: " + e.getMessage(), e);
        }
    }

    private List<RagDtos.SourceReference> readSources(String sourcesJson) {
        try {
            JsonNode root = objectMapper.readTree(sourcesJson);
            List<RagDtos.SourceReference> sources = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    sources.add(objectMapper.treeToValue(node, RagDtos.SourceReference.class));
                }
            }
            return sources;
        } catch (Exception e) {
            throw new IllegalStateException("读取聊天来源失败: " + e.getMessage(), e);
        }
    }
}
