package com.collect.ai.rag.controller;

import com.collect.ai.rag.dto.AiChatDtos;
import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.service.AiChatHistoryService;
import com.collect.ai.rag.service.RagService;
import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI chat history")
@RestController
@RequestMapping("/api/ai/chat/conversations")
@RequiredArgsConstructor
@Slf4j
public class AiChatHistoryController {

    private final AiChatHistoryService historyService;
    private final RagService ragService;

    @GetMapping
    public R<List<AiChatDtos.ConversationResponse>> listConversations() {
        requireChatPermission();
        return R.ok(historyService.listConversations(LoginUtils.getUserId()));
    }

    @PostMapping
    public R<AiChatDtos.ConversationResponse> createConversation() {
        requireChatPermission();
        return R.ok(historyService.createConversation(LoginUtils.getUserId()));
    }

    @GetMapping("/{conversationId}/messages")
    public R<List<AiChatDtos.MessageResponse>> listMessages(@PathVariable Long conversationId) {
        requireChatPermission();
        return R.ok(historyService.listMessages(LoginUtils.getUserId(), conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public R<AiChatDtos.MessageResponse> sendMessage(
            @PathVariable Long conversationId,
            @RequestBody AiChatDtos.SendMessageRequest request) {
        requireChatPermission();
        Long userId = LoginUtils.getUserId();
        historyService.requireOwnedConversation(userId, conversationId);
        if (request == null || request.getQuestion() == null
                || request.getQuestion().isBlank() || request.getQuestion().length() > 2000) {
            throw new IllegalArgumentException("问题不能为空且不能超过 2000 个字符");
        }

        RagDtos.QueryRequest query = new RagDtos.QueryRequest();
        query.setQuestion(request.getQuestion().trim());
        query.setTopK(request.getTopK());
        historyService.saveUserQuestion(userId, conversationId, query.getQuestion());
        RagDtos.QueryResponse answer;
        try {
            answer = ragService.query(query);
        } catch (RuntimeException e) {
            log.error("AI 聊天回答失败，conversationId={}", conversationId, e);
            return R.ok(historyService.saveAssistantFailure(userId, conversationId));
        }
        return R.ok(historyService.saveAssistantAnswer(userId, conversationId, answer));
    }

    @DeleteMapping("/{conversationId}")
    public R<Void> deleteConversation(@PathVariable Long conversationId) {
        requireChatPermission();
        historyService.deleteConversation(LoginUtils.getUserId(), conversationId);
        return R.ok();
    }

    private void requireChatPermission() {
        if (!LoginUtils.hasPermission("ai:chat")) {
            throw new BizException("无权使用 AI 助手");
        }
    }
}
