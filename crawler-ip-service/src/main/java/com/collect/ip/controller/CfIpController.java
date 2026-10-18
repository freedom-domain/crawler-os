package com.collect.ip.controller;

import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import com.collect.ip.dto.CfIpCandidatesReq;
import com.collect.ip.dto.CfIpProbeReq;
import com.collect.ip.dto.CfIpRangeProbeReq;
import com.collect.ip.dto.CfIpRangeRegionReq;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.entity.CfIpRange;
import com.collect.ip.service.CfIpCandidateService;
import com.collect.ip.service.CfIpRangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Cloudflare IP 优选")
@RestController
@RequestMapping("/api/cf-ip")
public class CfIpController {

    private final CfIpCandidateService service;
    private final CfIpRangeService rangeService;

    public CfIpController(CfIpCandidateService service, CfIpRangeService rangeService) {
        this.service = service;
        this.rangeService = rangeService;
    }

    @Operation(summary = "获取 Cloudflare IP 候选列表")
    @GetMapping
    public R<List<CfIpCandidate>> list() {
        requirePermission();
        return R.ok(service.list());
    }

    @Operation(summary = "获取 Cloudflare 官方 IP 网段")
    @GetMapping("/ranges")
    public R<List<CfIpRange>> ranges() {
        requirePermission();
        return R.ok(rangeService.list());
    }

    @Operation(summary = "初始化或更新 Cloudflare 官方 IP 网段")
    @PostMapping("/ranges/initialize")
    public R<Integer> initializeRanges() {
        requirePermission();
        return R.ok(rangeService.initializeOfficialRanges());
    }

    @Operation(summary = "更新 Cloudflare 网段地区标签")
    @PutMapping("/ranges/{id}/region")
    public R<CfIpRange> updateRangeRegion(@PathVariable Long id,
                                          @Valid @RequestBody CfIpRangeRegionReq req) {
        requirePermission();
        return R.ok(rangeService.updateRegion(id, req.getRegion()));
    }

    @Operation(summary = "对每个 Cloudflare 网段执行有限地址抽样测试")
    @PostMapping("/ranges/probe")
    public R<List<CfIpCandidate>> probeRangeSamples(@Valid @RequestBody CfIpRangeProbeReq req) {
        requirePermission();
        return R.ok(rangeService.probeSamples(req.getSamplesPerRange(), req.getTlsServerName()));
    }

    @Operation(summary = "批量添加 Cloudflare IP 候选")
    @PostMapping
    public R<Integer> add(@Valid @RequestBody CfIpCandidatesReq req) {
        requirePermission();
        return R.ok(service.addCandidates(req.getIps()));
    }

    @Operation(summary = "批量测试 Cloudflare IP 的 TCP 连接、TLS 和 HTTPS 下载速度")
    @PostMapping("/probe")
    public R<List<CfIpCandidate>> probe(@Valid @RequestBody CfIpProbeReq req) {
        requirePermission();
        return R.ok(service.probe(req.getIds(), req.getTlsServerName()));
    }

    @Operation(summary = "删除 Cloudflare IP 候选")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requirePermission();
        service.delete(id);
        return R.ok();
    }

    private void requirePermission() {
        if (!LoginUtils.hasPermission("cf-ip:manage")) {
            throw new BizException("无权限执行该操作");
        }
    }
}
