package com.collect.ai.rag.controller;

import com.collect.ai.rag.dto.AiChatDtos;
import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.service.AiChatHistoryService;
import com.collect.ai.rag.service.RagService;
import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
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
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "AI chat history")
@RestController
@RequestMapping("/api/ai/chat/conversations")
@RequiredArgsConstructor
@Slf4j
public class AiChatHistoryController {

    private final AiChatHistoryService historyService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

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

    @PostMapping(value = "/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> sendMessageStream(
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

        StreamingResponseBody stream = output -> {
            StringBuilder answerText = new StringBuilder();
            RagDtos.QueryResponse answer;
            try {
                answer = ragService.queryForLoggedInUser(query, token -> {
                    answerText.append(token);
                    try {
                        writeEvent(output, "token", token);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (Exception e) {
                log.error("AI 聊天流式回答失败，conversationId={}", conversationId, e);
                historyService.saveAssistantFailure(userId, conversationId);
                writeEvent(output, "error", "回答生成失败，请稍后重试。");
                return;
            }
            answer.setAnswer(answerText.toString());
            AiChatDtos.MessageResponse saved =
                    historyService.saveAssistantAnswer(userId, conversationId, answer);
            writeEvent(output, "complete", saved);
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream);
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

    private void writeEvent(java.io.OutputStream output, String event, Object data) throws IOException {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("序列化聊天流事件失败", e);
        }
        String frame = "event: " + event + "\ndata: " + payload + "\n\n";
        output.write(frame.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
