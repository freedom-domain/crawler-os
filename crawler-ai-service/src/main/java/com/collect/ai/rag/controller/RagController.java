package com.collect.ai.rag.controller;

import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.dto.RagConfigRequest;
import com.collect.ai.rag.dto.RagConfigResponse;
import com.collect.ai.rag.dto.ChatModelRequest;
import com.collect.ai.rag.dto.ChatModelResponse;
import com.collect.ai.rag.dto.ModelDiscoveryRequest;
import com.collect.ai.rag.dto.KnowledgeBaseDtos;
import com.collect.ai.rag.dto.VectorModelRequest;
import com.collect.ai.rag.dto.VectorModelResponse;
import com.collect.ai.rag.dto.ChunkStrategyRequest;
import com.collect.ai.rag.dto.ChunkStrategyResponse;
import com.collect.ai.rag.dto.RerankModelRequest;
import com.collect.ai.rag.dto.RerankModelResponse;
import com.collect.ai.rag.dto.AiAssistantDtos;
import com.collect.ai.rag.dto.AiKnowledgeBaseDtos;
import com.collect.ai.rag.service.AiAssistantService;
import com.collect.ai.rag.service.AiKnowledgeBaseService;
import com.collect.ai.rag.service.ChatModelService;
import com.collect.ai.rag.service.ModelDiscoveryService;
import com.collect.ai.rag.service.RagConfigService;
import com.collect.ai.rag.service.RagIndexRebuildTaskService;
import com.collect.ai.rag.service.RagService;
import com.collect.ai.rag.service.VectorModelService;
import com.collect.ai.rag.service.ChunkStrategyService;
import com.collect.ai.rag.service.RerankModelService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "AI RAG")
@RestController
@RequestMapping("/api/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final RagConfigService ragConfigService;
    private final VectorModelService vectorModelService;
    private final ChunkStrategyService chunkStrategyService;
    private final ChatModelService chatModelService;
    private final RerankModelService rerankModelService;
    private final ModelDiscoveryService modelDiscoveryService;
    private final AiAssistantService aiAssistantService;
    private final AiKnowledgeBaseService aiKnowledgeBaseService;
    private final RagIndexRebuildTaskService ragIndexRebuildTaskService;

    @Operation(summary = "读取知识库上传文档列表")
    @GetMapping("/knowledge-bases/{id}/documents")
    public R<java.util.List<com.collect.ai.rag.dto.AiKnowledgeDocumentDtos.Response>>
            knowledgeBaseDocuments(@PathVariable Long id) {
        requireAdmin();
        return R.ok(ragService.listKnowledgeDocuments(id));
    }

    @Operation(summary = "上传知识库文档")
    @PostMapping("/knowledge-bases/{id}/documents")
    public R<com.collect.ai.rag.dto.AiKnowledgeDocumentDtos.Response> uploadKnowledgeDocument(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        requireAdmin();
        return R.ok(ragService.uploadKnowledgeDocument(id, file));
    }

    @Operation(summary = "读取知识库列表")
    @GetMapping("/knowledge-bases")
    public R<java.util.List<AiKnowledgeBaseDtos.Response>> knowledgeBases() {
        requireAdmin();
        return R.ok(aiKnowledgeBaseService.list());
    }

    @Operation(summary = "分页查询知识库")
    @GetMapping("/knowledge-bases/page")
    public R<IPage<AiKnowledgeBaseDtos.Response>> knowledgeBasePage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "15") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean enabled) {
        requireAdmin();
        return R.ok(aiKnowledgeBaseService.page(page, pageSize, keyword, sourceType, enabled));
    }

    @Operation(summary = "创建知识库")
    @PostMapping("/knowledge-bases")
    public R<AiKnowledgeBaseDtos.Response> createKnowledgeBase(
            @RequestBody AiKnowledgeBaseDtos.Request request) {
        requireAdmin();
        return R.ok(aiKnowledgeBaseService.create(request));
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/knowledge-bases/{id}")
    public R<AiKnowledgeBaseDtos.Response> updateKnowledgeBase(
            @PathVariable Long id, @RequestBody AiKnowledgeBaseDtos.Request request) {
        requireAdmin();
        return R.ok(aiKnowledgeBaseService.update(id, request));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/knowledge-bases/{id}")
    public R<Void> deleteKnowledgeBase(@PathVariable Long id) {
        requireAdmin();
        aiKnowledgeBaseService.delete(id);
        return R.ok();
    }

    @Operation(summary = "读取智能体列表")
    @GetMapping("/assistants")
    public R<java.util.List<AiAssistantDtos.Response>> assistants() {
        requireAdmin();
        return R.ok(aiAssistantService.list());
    }

    @Operation(summary = "分页查询智能体")
    @GetMapping("/assistants/page")
    public R<IPage<AiAssistantDtos.Response>> assistantPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "15") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled) {
        requireAdmin();
        return R.ok(aiAssistantService.page(page, pageSize, keyword, enabled));
    }

    @Operation(summary = "读取智能体详情")
    @GetMapping("/assistants/{id}")
    public R<AiAssistantDtos.Response> assistant(@PathVariable Long id) {
        requireAdmin();
        return R.ok(aiAssistantService.get(id));
    }

    @Operation(summary = "创建智能体")
    @PostMapping("/assistants")
    public R<AiAssistantDtos.Response> createAssistant(@RequestBody AiAssistantDtos.Request request) {
        requireAdmin();
        return R.ok(aiAssistantService.create(request));
    }

    @Operation(summary = "更新智能体")
    @PutMapping("/assistants/{id}")
    public R<AiAssistantDtos.Response> updateAssistant(
            @PathVariable Long id, @RequestBody AiAssistantDtos.Request request) {
        requireAdmin();
        return R.ok(aiAssistantService.update(id, request));
    }

    @Operation(summary = "删除智能体")
    @DeleteMapping("/assistants/{id}")
    public R<Void> deleteAssistant(@PathVariable Long id) {
        requireAdmin();
        aiAssistantService.delete(id);
        return R.ok();
    }

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

    @Operation(summary = "分页查询向量模型")
    @GetMapping("/vector-models/page")
    public R<IPage<VectorModelResponse>> vectorModelPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "15") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String apiKeyStatus) {
        requireAdmin();
        return R.ok(vectorModelService.page(page, pageSize, keyword, provider, apiKeyStatus));
    }

    @Operation(summary = "读取文档分块策略列表")
    @GetMapping("/chunk-strategies")
    public R<java.util.List<ChunkStrategyResponse>> chunkStrategies() {
        requireAdmin();
        return R.ok(chunkStrategyService.list());
    }

    @Operation(summary = "创建文档分块策略")
    @PostMapping("/chunk-strategies")
    public R<ChunkStrategyResponse> createChunkStrategy(@RequestBody ChunkStrategyRequest request) {
        requireAdmin();
        return R.ok(chunkStrategyService.create(request));
    }

    @Operation(summary = "更新文档分块策略")
    @PutMapping("/chunk-strategies/{id}")
    public R<ChunkStrategyResponse> updateChunkStrategy(
            @PathVariable Long id, @RequestBody ChunkStrategyRequest request) {
        requireAdmin();
        return R.ok(chunkStrategyService.update(id, request));
    }

    @Operation(summary = "删除文档分块策略")
    @DeleteMapping("/chunk-strategies/{id}")
    public R<Void> deleteChunkStrategy(@PathVariable Long id) {
        requireAdmin();
        chunkStrategyService.delete(id);
        return R.ok();
    }

    @Operation(summary = "读取向量模型 API Key")
    @GetMapping("/vector-models/{id}/api-key")
    public R<String> getVectorModelApiKey(@PathVariable Long id, HttpServletResponse response) {
        requireAdmin();
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        return R.ok(vectorModelService.getApiKey(id));
    }

    @Operation(summary = "更新向量模型 API Key")
    @PutMapping("/vector-models/{id}/api-key")
    public R<Void> updateVectorModelApiKey(@PathVariable Long id,
                                           @RequestBody VectorModelRequest request) {
        requireAdmin();
        if (request == null) {
            throw new BizException("API Key 请求不能为空");
        }
        vectorModelService.updateApiKey(id, request.getApiKey());
        return R.ok();
    }

    @Operation(summary = "创建向量模型")
    @PostMapping("/vector-models")
    public R<VectorModelResponse> createVectorModel(@RequestBody VectorModelRequest request) {
        requireAdmin();
        return R.ok(vectorModelService.create(request));
    }

    @Operation(summary = "自动拉取向量模型列表")
    @PostMapping("/vector-models/discover")
    public R<java.util.List<String>> discoverVectorModels(@RequestBody ModelDiscoveryRequest request) {
        requireAdmin();
        return R.ok(modelDiscoveryService.discoverVectorModels(request));
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

    @Operation(summary = "分页查询聊天模型")
    @GetMapping("/chat-models/page")
    public R<IPage<ChatModelResponse>> chatModelPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "15") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String apiKeyStatus) {
        requireAdmin();
        return R.ok(chatModelService.page(page, pageSize, keyword, provider, apiKeyStatus));
    }

    @Operation(summary = "自动拉取聊天模型列表")
    @PostMapping("/chat-models/discover")
    public R<java.util.List<String>> discoverChatModels(@RequestBody ModelDiscoveryRequest request) {
        requireAdmin();
        return R.ok(modelDiscoveryService.discoverChatModels(request));
    }

    @Operation(summary = "读取聊天模型 API Key")
    @GetMapping("/chat-models/{id}/api-key")
    public R<String> getChatModelApiKey(@PathVariable Long id, HttpServletResponse response) {
        requireAdmin();
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        return R.ok(chatModelService.getApiKey(id));
    }

    @Operation(summary = "更新聊天模型 API Key")
    @PutMapping("/chat-models/{id}/api-key")
    public R<Void> updateChatModelApiKey(@PathVariable Long id,
                                         @RequestBody ChatModelRequest request) {
        requireAdmin();
        if (request == null) {
            throw new BizException("API Key 请求不能为空");
        }
        chatModelService.updateApiKey(id, request.getApiKey());
        return R.ok();
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

    @Operation(summary = "读取重排模型列表")
    @GetMapping("/rerank-models")
    public R<java.util.List<RerankModelResponse>> rerankModels() {
        requireAdmin();
        return R.ok(rerankModelService.list());
    }

    @Operation(summary = "分页查询重排模型")
    @GetMapping("/rerank-models/page")
    public R<IPage<RerankModelResponse>> rerankModelPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "15") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String apiKeyStatus) {
        requireAdmin();
        return R.ok(rerankModelService.page(page, pageSize, keyword, provider, apiKeyStatus));
    }

    @Operation(summary = "自动拉取重排模型列表")
    @PostMapping("/rerank-models/discover")
    public R<java.util.List<String>> discoverRerankModels(@RequestBody ModelDiscoveryRequest request) {
        requireAdmin();
        return R.ok(modelDiscoveryService.discoverRerankModels(request));
    }

    @Operation(summary = "读取重排模型 API Key")
    @GetMapping("/rerank-models/{id}/api-key")
    public R<String> getRerankModelApiKey(@PathVariable Long id, HttpServletResponse response) {
        requireAdmin();
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        return R.ok(rerankModelService.getApiKey(id));
    }

    @Operation(summary = "更新重排模型 API Key")
    @PutMapping("/rerank-models/{id}/api-key")
    public R<Void> updateRerankModelApiKey(@PathVariable Long id,
                                           @RequestBody RerankModelRequest request) {
        requireAdmin();
        if (request == null) {
            throw new BizException("API Key 请求不能为空");
        }
        rerankModelService.updateApiKey(id, request.getApiKey());
        return R.ok();
    }

    @Operation(summary = "创建重排模型")
    @PostMapping("/rerank-models")
    public R<RerankModelResponse> createRerankModel(@RequestBody RerankModelRequest request) {
        requireAdmin();
        return R.ok(rerankModelService.create(request));
    }

    @Operation(summary = "更新重排模型")
    @PutMapping("/rerank-models/{id}")
    public R<RerankModelResponse> updateRerankModel(@PathVariable Long id,
                                                    @RequestBody RerankModelRequest request) {
        requireAdmin();
        return R.ok(rerankModelService.update(id, request));
    }

    @Operation(summary = "删除重排模型")
    @DeleteMapping("/rerank-models/{id}")
    public R<Void> deleteRerankModel(@PathVariable Long id) {
        requireAdmin();
        rerankModelService.delete(id);
        return R.ok();
    }

    @Operation(summary = "同步 ES 文档并建立向量索引")
    @PostMapping("/sync")
    public R<RagDtos.SyncTaskResponse> sync(
            @RequestParam(required = false) Long knowledgeBaseId) {
        requirePermission("search:query");
        return R.ok(ragIndexRebuildTaskService.start(knowledgeBaseId));
    }

    @Operation(summary = "重建向量索引")
    @PostMapping("/vector-index/rebuild")
    public R<RagDtos.SyncTaskResponse> rebuildVectorIndex(
            @RequestParam(required = false) Long knowledgeBaseId) {
        requireAdmin();
        return R.ok(ragIndexRebuildTaskService.start(knowledgeBaseId));
    }

    @Operation(summary = "查询向量索引重建任务状态")
    @GetMapping("/sync/tasks/{taskId}")
    public R<RagDtos.SyncTaskResponse> syncTask(@PathVariable String taskId) {
        requirePermission("search:query");
        return R.ok(ragIndexRebuildTaskService.get(taskId));
    }

    @Operation(summary = "读取最新向量索引重建任务")
    @GetMapping("/sync/tasks/latest")
    public R<RagDtos.SyncTaskResponse> latestSyncTask(
            @RequestParam(required = false) Long knowledgeBaseId) {
        requirePermission("search:query");
        return R.ok(ragIndexRebuildTaskService.latest(knowledgeBaseId));
    }

    @Operation(summary = "读取知识库索引状态")
    @GetMapping("/knowledge/stats")
    public R<KnowledgeBaseDtos.Stats> knowledgeBaseStats(
            @RequestParam(required = false) Long knowledgeBaseId) {
        requireAdmin();
        return R.ok(ragService.getKnowledgeBaseStats(knowledgeBaseId));
    }

    @Operation(summary = "分页搜索知识库文档")
    @GetMapping("/knowledge/documents")
    public R<KnowledgeBaseDtos.DocumentPage> knowledgeBaseDocuments(
            @RequestParam Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "true") boolean includeExcluded,
            @RequestParam(required = false) Boolean vectorized) {
        requireAdmin();
        return R.ok(ragService.listKnowledgeBaseDocuments(
                knowledgeBaseId, page, pageSize, keyword, includeExcluded, vectorized));
    }

    @Operation(summary = "读取知识库文档详情")
    @GetMapping("/knowledge/documents/{id}")
    public R<KnowledgeBaseDtos.DocumentDetail> knowledgeBaseDocument(
            @PathVariable String id,
            @RequestParam(required = false) Long knowledgeBaseId) {
        requireAdmin();
        return R.ok(ragService.getKnowledgeBaseDocument(knowledgeBaseId, id));
    }

    @Operation(summary = "读取知识库文档向量分块详情")
    @GetMapping("/knowledge/documents/{id}/chunks")
    public R<java.util.List<KnowledgeBaseDtos.VectorChunk>> knowledgeBaseDocumentChunks(
            @PathVariable String id,
            @RequestParam(required = false) Long knowledgeBaseId) {
        requireAdmin();
        return R.ok(ragService.getKnowledgeBaseDocumentChunks(knowledgeBaseId, id));
    }

    @Operation(summary = "重新向量化单篇知识库文档")
    @PostMapping("/knowledge/documents/{id}/vectorize")
    public R<RagDtos.DocumentVectorizationResult> vectorizeDocument(
            @PathVariable String id,
            @RequestParam(required = false) Long knowledgeBaseId) {
        requireAdmin();
        return R.ok(ragService.vectorizeDocument(knowledgeBaseId, id));
    }

    @Operation(summary = "从知识库排除文档")
    @DeleteMapping("/knowledge/documents/{id}")
    public R<Void> excludeKnowledgeBaseDocument(
            @PathVariable String id,
            @RequestParam(required = false) Long knowledgeBaseId) {
        requireAdmin();
        ragService.excludeKnowledgeBaseDocument(knowledgeBaseId, id);
        return R.ok();
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
