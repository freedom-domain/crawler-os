package com.collect.ai.rag.controller;

import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.dto.RagConfigRequest;
import com.collect.ai.rag.dto.RagConfigResponse;
import com.collect.ai.rag.service.RagConfigService;
import com.collect.ai.rag.service.RagService;
import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
