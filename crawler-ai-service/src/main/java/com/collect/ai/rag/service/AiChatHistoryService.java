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
import java.util.Collections;
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

    public List<RagDtos.ChatHistoryMessage> getRecentContext(Long userId, Long conversationId) {
        requireOwnedConversation(userId, conversationId);
        List<AiChatMessage> messages =
                messageMapper.selectRecentContextByConversationId(conversationId);
        int historyTokenBudget = Math.max(256, ragProperties.getRag().getContextLength() / 2);
        int remainingTokens = historyTokenBudget;
        List<RagDtos.ChatHistoryMessage> recentContext = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessage message = messages.get(i);
            String content = message.getContent() == null ? "" : message.getContent();
            int messageTokens = estimateTokens(content) + 4;
            if (messageTokens > remainingTokens) {
                if (recentContext.isEmpty() && remainingTokens > 4) {
                    content = truncateToTokenBudget(content, remainingTokens - 4);
                    addContextMessage(recentContext, message, content);
                }
                break;
            }
            addContextMessage(recentContext, message, content);
            remainingTokens -= messageTokens;
        }
        Collections.reverse(recentContext);
        return recentContext;
    }

    private static void addContextMessage(
            List<RagDtos.ChatHistoryMessage> context, AiChatMessage message, String content) {
        RagDtos.ChatHistoryMessage item = new RagDtos.ChatHistoryMessage();
        item.setRole(message.getRole());
        item.setContent(content);
        context.add(item);
    }

    private static String truncateToTokenBudget(String content, int tokenBudget) {
        int low = 0;
        int high = content.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (estimateTokens(content.substring(0, middle)) <= tokenBudget) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        if (low > 0 && low < content.length()
                && Character.isHighSurrogate(content.charAt(low - 1))
                && Character.isLowSurrogate(content.charAt(low))) {
            low--;
        }
        return content.substring(0, low);
    }

    // Estimate token usage for history budgeting without coupling to a provider tokenizer.
    private static int estimateTokens(String content) {
        int tokens = 0;
        int plainCharacters = 0;
        for (int offset = 0; offset < content.length();) {
            int codePoint = content.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isCjk(codePoint)) {
                tokens += (plainCharacters + 3) / 4 + 1;
                plainCharacters = 0;
            } else {
                plainCharacters++;
            }
        }
        return tokens + (plainCharacters + 3) / 4;
    }

    private static boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF);
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

    @Transactional
    public AiChatDtos.MessageResponse completeAssistantStream(
            Long userId,
            Long conversationId,
            Long messageId,
            RagDtos.QueryResponse answer,
            String content) {
        findOwnedConversation(userId, conversationId);
        AiChatMessage message = findAssistantMessage(conversationId, messageId);
        if (!"STREAMING".equals(message.getStatus())) {
            throw new IllegalStateException("聊天流已结束");
        }
        message.setContent(content == null ? "" : content);
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
            RagDtos.QueryResponse answer) {
        findOwnedConversation(userId, conversationId);
        LocalDateTime now = LocalDateTime.now();

        AiChatMessage assistantMessage = new AiChatMessage();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer.getAnswer() == null ? "" : answer.getAnswer());
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
