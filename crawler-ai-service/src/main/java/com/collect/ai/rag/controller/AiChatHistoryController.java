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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;

@Tag(name = "AI chat history")
@RestController
@RequestMapping("/api/ai/chat/conversations")
@Slf4j
public class AiChatHistoryController {

    private final AiChatHistoryService historyService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;
    private final Executor streamExecutor;

    public AiChatHistoryController(
            AiChatHistoryService historyService,
            RagService ragService,
            ObjectMapper objectMapper,
            @Qualifier("aiChatStreamExecutor") Executor streamExecutor) {
        this.historyService = historyService;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
        this.streamExecutor = streamExecutor;
    }

    @GetMapping
    public R<List<AiChatDtos.ConversationResponse>> listConversations() {
        requireChatPermission();
        return R.ok(historyService.listConversations(LoginUtils.getUserId()));
    }

    @GetMapping("/context-window")
    public R<AiChatDtos.ContextWindowResponse> getContextWindow() {
        requireChatPermission();
        AiChatDtos.ContextWindowResponse response = new AiChatDtos.ContextWindowResponse();
        response.setContextLength(historyService.getContextLength());
        return R.ok(response);
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

    @DeleteMapping("/{conversationId}/messages")
    public R<Void> clearMessages(@PathVariable Long conversationId) {
        requireChatPermission();
        historyService.clearMessages(LoginUtils.getUserId(), conversationId);
        return R.ok();
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
        List<RagDtos.ChatHistoryMessage> context =
                historyService.getRecentContext(userId, conversationId);
        historyService.saveUserQuestion(userId, conversationId, query.getQuestion());
        RagDtos.QueryResponse answer;
        try {
            answer = ragService.queryForLoggedInUser(query, context);
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
        List<RagDtos.ChatHistoryMessage> context =
                historyService.getRecentContext(userId, conversationId);
        historyService.saveUserQuestion(userId, conversationId, query.getQuestion());
        AiChatDtos.MessageResponse message =
                historyService.beginAssistantStream(userId, conversationId);
        try {
            streamExecutor.execute(() ->
                    generateAssistantStream(userId, conversationId, message.getId(), query, context));
        } catch (RuntimeException e) {
            log.error("启动 AI 聊天流失败，conversationId={}", conversationId, e);
            historyService.failAssistantStream(userId, conversationId, message.getId());
        }
        StreamingResponseBody stream =
                assistantStream(userId, conversationId, message.getId(), 0);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream);
    }

    @GetMapping(value = "/{conversationId}/messages/{messageId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> resumeMessageStream(
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "0") int offset) {
        requireChatPermission();
        Long userId = LoginUtils.getUserId();
        AiChatDtos.MessageResponse message =
                historyService.getAssistantStream(userId, conversationId, messageId);
        String content = message.getContent() == null ? "" : message.getContent();
        if (offset < 0 || offset > content.length()) {
            throw new IllegalArgumentException("流式恢复位置无效");
        }
        StreamingResponseBody stream =
                assistantStream(userId, conversationId, messageId, offset);
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

    private void generateAssistantStream(
            Long userId,
            Long conversationId,
            Long messageId,
            RagDtos.QueryRequest query,
            List<RagDtos.ChatHistoryMessage> context) {
        StringBuilder answerText = new StringBuilder();
        try {
            RagDtos.QueryResponse answer = ragService.queryForLoggedInUser(query, context, token -> {
                answerText.append(token);
                historyService.appendAssistantStream(messageId, token);
            });
            answer.setAnswer(answerText.toString());
            historyService.completeAssistantStream(
                    userId, conversationId, messageId, answer, answerText.toString());
        } catch (Exception e) {
            log.error("AI 聊天流式回答失败，conversationId={}, messageId={}",
                    conversationId, messageId, e);
            try {
                historyService.failAssistantStream(userId, conversationId, messageId);
            } catch (RuntimeException persistenceError) {
                log.error("保存 AI 聊天流失败状态失败，conversationId={}, messageId={}",
                        conversationId, messageId, persistenceError);
            }
        }
    }

    private void writeAssistantStream(
            java.io.OutputStream output,
            Long userId,
            Long conversationId,
            Long messageId,
            int initialOffset) throws IOException {
        int offset = initialOffset;
        long lastHeartbeat = System.nanoTime();
        writeEvent(output, "start",
                historyService.getAssistantStream(userId, conversationId, messageId));
        while (true) {
            AiChatDtos.MessageResponse message =
                    historyService.getAssistantStream(userId, conversationId, messageId);
            String content = message.getContent() == null ? "" : message.getContent();
            if (content.length() > offset) {
                writeEvent(output, "token", content.substring(offset));
                offset = content.length();
                lastHeartbeat = System.nanoTime();
            }
            if (!"STREAMING".equals(message.getStatus())) {
                if ("SUCCESS".equals(message.getStatus())) {
                    writeEvent(output, "complete", message);
                } else {
                    writeEvent(output, "error", content);
                }
                return;
            }
            if (System.nanoTime() - lastHeartbeat > java.time.Duration.ofSeconds(15).toNanos()) {
                output.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                lastHeartbeat = System.nanoTime();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("聊天流读取被中断", e);
            }
        }
    }

    private StreamingResponseBody assistantStream(
            Long userId, Long conversationId, Long messageId, int offset) {
        return output -> {
            try {
                writeAssistantStream(output, userId, conversationId, messageId, offset);
            } catch (RuntimeException e) {
                log.error("读取 AI 聊天流失败，conversationId={}, messageId={}",
                        conversationId, messageId, e);
                writeEvent(output, "error", "读取聊天回答失败，请重新打开会话重试。");
            } catch (IOException e) {
                log.warn("AI 聊天流连接中断，conversationId={}, messageId={}",
                        conversationId, messageId, e);
            }
        };
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
