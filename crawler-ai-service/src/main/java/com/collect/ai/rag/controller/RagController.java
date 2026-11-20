package com.collect.ai.rag.controller;

import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.dto.RagConfigRequest;
import com.collect.ai.rag.dto.RagConfigResponse;
import com.collect.ai.rag.dto.ChatModelRequest;
import com.collect.ai.rag.dto.ChatModelResponse;
import com.collect.ai.rag.dto.VectorModelRequest;
import com.collect.ai.rag.dto.VectorModelResponse;
import com.collect.ai.rag.service.ChatModelService;
import com.collect.ai.rag.service.RagConfigService;
import com.collect.ai.rag.service.RagService;
import com.collect.ai.rag.service.VectorModelService;
import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI RAG")
@RestController
@RequestMapping("/api/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final RagConfigService ragConfigService;
    private final VectorModelService vectorModelService;
    private final ChatModelService chatModelService;

    @Operation(summary = "读取当前环境的 RAG 配置")
    @GetMapping("/config")
    public R<RagConfigResponse> getConfig() {
        requireAdmin();
        return R.ok(ragConfigService.getConfig());
    }

    @Operation(summary = "更新当前环境的 RAG 配置")
    @PutMapping("/config")
    public R<RagConfigResponse> updateConfig(@RequestBody RagConfigRequest request) {
        requireAdmin();
        return R.ok(ragConfigService.update(request));
    }

    @Operation(summary = "读取向量模型列表")
    @GetMapping("/vector-models")
    public R<java.util.List<VectorModelResponse>> vectorModels() {
        requireAdmin();
        return R.ok(vectorModelService.list());
    }

    @Operation(summary = "创建向量模型")
    @PostMapping("/vector-models")
    public R<VectorModelResponse> createVectorModel(@RequestBody VectorModelRequest request) {
        requireAdmin();
        return R.ok(vectorModelService.create(request));
    }

    @Operation(summary = "更新向量模型")
    @PutMapping("/vector-models/{id}")
    public R<VectorModelResponse> updateVectorModel(@PathVariable Long id,
                                                    @RequestBody VectorModelRequest request) {
        requireAdmin();
        return R.ok(vectorModelService.update(id, request));
    }

    @Operation(summary = "删除向量模型")
    @DeleteMapping("/vector-models/{id}")
    public R<Void> deleteVectorModel(@PathVariable Long id) {
        requireAdmin();
        vectorModelService.delete(id);
        return R.ok();
    }

    @Operation(summary = "读取聊天模型列表")
    @GetMapping("/chat-models")
    public R<java.util.List<ChatModelResponse>> chatModels() {
        requireAdmin();
        return R.ok(chatModelService.list());
    }

    @Operation(summary = "创建聊天模型")
    @PostMapping("/chat-models")
    public R<ChatModelResponse> createChatModel(@RequestBody ChatModelRequest request) {
        requireAdmin();
        return R.ok(chatModelService.create(request));
    }

    @Operation(summary = "更新聊天模型")
    @PutMapping("/chat-models/{id}")
    public R<ChatModelResponse> updateChatModel(@PathVariable Long id,
                                                @RequestBody ChatModelRequest request) {
        requireAdmin();
        return R.ok(chatModelService.update(id, request));
    }

    @Operation(summary = "删除聊天模型")
    @DeleteMapping("/chat-models/{id}")
    public R<Void> deleteChatModel(@PathVariable Long id) {
        requireAdmin();
        chatModelService.delete(id);
        return R.ok();
    }

    @Operation(summary = "同步 ES 文档并建立向量索引")
    @PostMapping("/sync")
    public R<RagDtos.SyncResult> sync() {
        requirePermission("search:query");
        return R.ok(ragService.syncContentIndex());
    }

    @Operation(summary = "基于 ES 爬取文档进行 RAG 问答")
    @PostMapping("/query")
    public R<RagDtos.QueryResponse> query(@RequestBody RagDtos.QueryRequest request) {
        requirePermissionIfLoggedIn("search:query");
        return R.ok(ragService.query(request));
    }

    private void requirePermission(String code) {
        if (!LoginUtils.hasPermission(code)) {
            throw new BizException("无权限执行该操作");
        }
    }

    private void requirePermissionIfLoggedIn(String code) {
        try {
            LoginUtils.getLoginUser();
        } catch (Exception e) {
            return;
        }
        if (!LoginUtils.hasPermission(code)) {
            throw new BizException("无权限执行该操作");
        }
    }

    private void requireAdmin() {
        if (!"admin".equals(LoginUtils.getLoginUser().getRoleCode())) {
            throw new BizException("仅管理员可管理 RAG 配置");
        }
    }
}
