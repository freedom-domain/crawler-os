package com.collect.ip.controller;

import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import com.collect.ip.dto.CfIpCandidatesDeleteReq;
import com.collect.ip.dto.CfIpCandidatesReq;
import com.collect.ip.dto.CfIpProbeReq;
import com.collect.ip.dto.CfIpRangeEnabledReq;
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
    @GetMapping({"", "/"})
    public R<List<CfIpCandidate>> list() {
        requirePermission();
        return R.ok(service.list());
    }

    @Operation(summary = "公开获取随机可连接的 Cloudflare IP")
    @GetMapping(value = "/available", produces = "text/plain; charset=UTF-8")
    public String available(@RequestParam(value = "count", defaultValue = "10") int count) {
        return String.join("\n", service.randomAvailableIpAddresses(count));
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

    @Operation(summary = "启用或停用 Cloudflare 网段优选")
    @PutMapping("/ranges/{id}/enabled")
    public R<CfIpRange> updateRangeEnabled(@PathVariable Long id,
                                           @Valid @RequestBody CfIpRangeEnabledReq req) {
        requirePermission();
        return R.ok(rangeService.updateEnabled(id, req.getEnabled()));
    }

    @Operation(summary = "将已启用 IPv4 网段的全部可用地址导入 IP 候选库")
    @PostMapping("/ranges/import-enabled-ipv4")
    public R<Integer> importEnabledIpv4Addresses() {
        requirePermission();
        return R.ok(rangeService.importEnabledIpv4Addresses());
    }

    @Operation(summary = "对已启用的 Cloudflare 网段执行有限地址抽样测试")
    @PostMapping("/ranges/probe")
    public R<List<CfIpCandidate>> probeRangeSamples(@Valid @RequestBody CfIpRangeProbeReq req) {
        requirePermission();
        return R.ok(rangeService.probeSamples(req.getSamplesPerRange(), req.getTlsServerName()));
    }

    @Operation(summary = "批量添加 Cloudflare IP 候选")
    @PostMapping({"", "/"})
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

    @Operation(summary = "批量删除 Cloudflare IP 候选")
    @DeleteMapping("/batch")
    public R<Integer> deleteBatch(@Valid @RequestBody CfIpCandidatesDeleteReq req) {
        requirePermission();
        return R.ok(service.deleteCandidates(req.getIds()));
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
