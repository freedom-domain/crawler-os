package com.collect.ai.rag.controller;

import com.collect.ai.rag.dto.AiChatDtos;
import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.service.AiChatHistoryService;
import com.collect.ai.rag.service.AiAssistantService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

@Tag(name = "AI chat history")
@RestController
@RequestMapping("/api/ai/chat/conversations")
@Slf4j
public class AiChatHistoryController {

    private final AiChatHistoryService historyService;
    private final AiAssistantService aiAssistantService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;
    private final Executor streamExecutor;
    private final ConcurrentMap<Long, Future<?>> streamTasks = new ConcurrentHashMap<>();

    public AiChatHistoryController(
            AiChatHistoryService historyService,
            AiAssistantService aiAssistantService,
            RagService ragService,
            ObjectMapper objectMapper,
            @Qualifier("aiChatStreamExecutor") Executor streamExecutor) {
        this.historyService = historyService;
        this.aiAssistantService = aiAssistantService;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
        this.streamExecutor = streamExecutor;
    }

    @GetMapping
    public R<List<AiChatDtos.ConversationResponse>> listConversations(
            @RequestParam(required = false) Long assistantId) {
        requireChatPermission();
        return R.ok(historyService.listConversations(LoginUtils.getUserId(), assistantId));
    }

    @GetMapping("/context-window")
    public R<AiChatDtos.ContextWindowResponse> getContextWindow() {
        requireChatPermission();
        AiChatDtos.ContextWindowResponse response = new AiChatDtos.ContextWindowResponse();
        response.setContextLength(historyService.getContextLength());
        return R.ok(response);
    }

    @GetMapping("/assistants")
    public R<java.util.List<com.collect.ai.rag.dto.AiAssistantDtos.Response>> listAssistants() {
        requireChatPermission();
        return R.ok(aiAssistantService.listEnabled());
    }

    @PostMapping
    public R<AiChatDtos.ConversationResponse> createConversation(
            @RequestParam(required = false) Long assistantId) {
        requireChatPermission();
        if (assistantId != null) {
            com.collect.ai.rag.dto.AiAssistantDtos.Response assistant =
                    aiAssistantService.get(assistantId);
            if (!Boolean.TRUE.equals(assistant.getEnabled())) {
                throw new BizException("AI 助手已停用");
            }
        }
        return R.ok(historyService.createConversation(LoginUtils.getUserId(), assistantId));
    }

    @PutMapping("/{conversationId}")
    public R<AiChatDtos.ConversationResponse> renameConversation(
            @PathVariable Long conversationId,
            @RequestBody AiChatDtos.RenameConversationRequest request) {
        requireChatPermission();
        return R.ok(historyService.renameConversation(
                LoginUtils.getUserId(), conversationId, request == null ? null : request.getTitle()));
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
        query.setChatModelId(request.getChatModelId());
        Long assistantId = historyService.getAssistantId(userId, conversationId);
        if (assistantId != null) {
            query.setKnowledgeBaseId(
                    aiAssistantService.get(assistantId).getKnowledgeBaseId());
        }
        List<RagDtos.ChatHistoryMessage> context =
                historyService.getSuccessfulContext(userId, conversationId);
        historyService.saveUserQuestion(userId, conversationId, query.getQuestion());
        RagDtos.QueryResponse answer;
        long startNanos = System.nanoTime();
        try {
            answer = ragService.queryForLoggedInUser(query, context);
        } catch (RuntimeException e) {
            log.error("AI 聊天回答失败，conversationId={}", conversationId, e);
            return R.ok(historyService.saveAssistantFailure(userId, conversationId));
        }
        long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        return R.ok(historyService.saveAssistantAnswer(userId, conversationId, answer, durationMs));
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
        query.setChatModelId(request.getChatModelId());
        Long streamingAssistantId = historyService.getAssistantId(userId, conversationId);
        if (streamingAssistantId != null) {
            query.setKnowledgeBaseId(
                    aiAssistantService.get(streamingAssistantId).getKnowledgeBaseId());
        }
        List<RagDtos.ChatHistoryMessage> context =
                historyService.getSuccessfulContext(userId, conversationId);
        historyService.saveUserQuestion(userId, conversationId, query.getQuestion());
        AiChatDtos.MessageResponse message =
                historyService.beginAssistantStream(userId, conversationId);
        try {
            Long messageId = message.getId();
            FutureTask<Void> task = new FutureTask<>(() -> {
                try {
                    generateAssistantStream(userId, conversationId, messageId, query, context);
                } finally {
                    streamTasks.remove(messageId);
                }
                return null;
            });
            streamTasks.put(messageId, task);
            streamExecutor.execute(task);
        } catch (RuntimeException e) {
            log.error("启动 AI 聊天流失败，conversationId={}", conversationId, e);
            streamTasks.remove(message.getId());
            historyService.failAssistantStream(userId, conversationId, message.getId());
        }
        StreamingResponseBody stream =
                assistantStream(userId, conversationId, message.getId(), 0, 0);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .header("X-Chat-Message-Id", message.getId().toString())
                .body(stream);
    }

    @PostMapping("/{conversationId}/messages/{messageId}/cancel")
    public R<AiChatDtos.MessageResponse> cancelMessageStream(
            @PathVariable Long conversationId,
            @PathVariable Long messageId) {
        requireChatPermission();
        Long userId = LoginUtils.getUserId();
        AiChatDtos.MessageResponse message =
                historyService.cancelAssistantStream(userId, conversationId, messageId);
        if ("CANCELLED".equals(message.getStatus())) {
            Future<?> task = streamTasks.remove(messageId);
            if (task != null) task.cancel(true);
        }
        return R.ok(message);
    }

    @GetMapping(value = "/{conversationId}/messages/{messageId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> resumeMessageStream(
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "0") int reasoningOffset) {
        requireChatPermission();
        Long userId = LoginUtils.getUserId();
        AiChatDtos.MessageResponse message =
                historyService.getAssistantStream(userId, conversationId, messageId);
        String content = message.getContent() == null ? "" : message.getContent();
        if (offset < 0 || offset > content.length()) {
            throw new IllegalArgumentException("流式恢复位置无效");
        }
        String reasoning = message.getReasoningContent() == null ? "" : message.getReasoningContent();
        if (reasoningOffset < 0 || reasoningOffset > reasoning.length()) {
            throw new IllegalArgumentException("思考内容恢复位置无效");
        }
        StreamingResponseBody stream =
                assistantStream(userId, conversationId, messageId, offset, reasoningOffset);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .header("X-Chat-Message-Id", messageId.toString())
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
            RagDtos.QueryResponse answer = ragService.queryForLoggedInUser(
                    query,
                    context,
                    token -> {
                        answerText.append(token);
                        historyService.appendAssistantStream(messageId, token);
                    },
                    reasoning -> historyService.appendAssistantReasoning(messageId, reasoning));
            answer.setAnswer(answerText.toString());
            long waitDurationMs = answer.getWaitDurationMs() == null ? 0 : answer.getWaitDurationMs();
            historyService.completeAssistantStream(
                    userId, conversationId, messageId, answer, answerText.toString(),
                    waitDurationMs,
                    answer.getThinkingDurationMs(),
                    answer.getGenerationDurationMs() == null ? 0 : answer.getGenerationDurationMs());
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
            int initialOffset,
            int initialReasoningOffset) throws IOException {
        int offset = initialOffset;
        int reasoningOffset = initialReasoningOffset;
        long lastHeartbeat = System.nanoTime();
        writeEvent(output, "start",
                historyService.getAssistantStream(userId, conversationId, messageId));
        while (true) {
            AiChatDtos.MessageResponse message =
                    historyService.getAssistantStream(userId, conversationId, messageId);
            String reasoning = message.getReasoningContent() == null ? "" : message.getReasoningContent();
            if (reasoning.length() > reasoningOffset) {
                writeEvent(output, "reasoning", reasoning.substring(reasoningOffset));
                reasoningOffset = reasoning.length();
                lastHeartbeat = System.nanoTime();
            }
            String content = message.getContent() == null ? "" : message.getContent();
            if (content.length() > offset) {
                writeEvent(output, "token", content.substring(offset));
                offset = content.length();
                lastHeartbeat = System.nanoTime();
            }
            if (!"STREAMING".equals(message.getStatus())) {
                if ("SUCCESS".equals(message.getStatus()) || "CANCELLED".equals(message.getStatus())) {
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
            Long userId, Long conversationId, Long messageId, int offset, int reasoningOffset) {
        return output -> {
            try {
                writeAssistantStream(output, userId, conversationId, messageId, offset, reasoningOffset);
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
