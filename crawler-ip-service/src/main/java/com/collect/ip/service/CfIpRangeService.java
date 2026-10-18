package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.entity.CfIpRange;
import com.collect.ip.mapper.CfIpCandidateMapper;
import com.collect.ip.mapper.CfIpRangeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class CfIpRangeService {

    private static final URI CLOUDFLARE_IPS_URI = URI.create("https://api.cloudflare.com/client/v4/ips");
    private static final int MAX_SAMPLES_PER_RANGE = 5;
    private static final int MAX_TOTAL_PROBES = 200;

    private final CfIpRangeMapper rangeMapper;
    private final CfIpCandidateMapper candidateMapper;
    private final CfIpProbeService probeService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CfIpRangeService(CfIpRangeMapper rangeMapper,
                            CfIpCandidateMapper candidateMapper,
                            CfIpProbeService probeService,
                            ObjectMapper objectMapper) {
        this.rangeMapper = rangeMapper;
        this.candidateMapper = candidateMapper;
        this.probeService = probeService;
        this.objectMapper = objectMapper;
    }

    public List<CfIpRange> list() {
        return rangeMapper.selectActive();
    }

    @Transactional
    public int initializeOfficialRanges() {
        List<CfIpRange> officialRanges = fetchOfficialRanges();
        if (officialRanges.isEmpty()) {
            throw new BizException("Cloudflare 官方未返回任何 IP 网段");
        }

        rangeMapper.deactivateAll();
        for (CfIpRange range : officialRanges) {
            rangeMapper.upsertOfficialRange(range);
        }
        log.info("Initialized {} official Cloudflare IP ranges", officialRanges.size());
        return officialRanges.size();
    }

    public CfIpRange updateRegion(Long id, String region) {
        String normalizedRegion = region == null ? "" : region.trim();
        if (normalizedRegion.isEmpty() || normalizedRegion.length() > 64) {
            throw new BizException("地区标签不能为空且不能超过 64 个字符");
        }
        CfIpRange range = rangeMapper.selectById(id);
        if (range == null || !Boolean.TRUE.equals(range.getActive())) {
            throw new BizException("Cloudflare 网段不存在，请刷新后重试");
        }
        range.setRegion(normalizedRegion);
        rangeMapper.updateById(range);
        candidateMapper.updateRangeRegion(range.getId(), normalizedRegion);
        return rangeMapper.selectById(id);
    }

    public List<CfIpCandidate> probeSamples(int samplesPerRange, String tlsServerName) {
        if (samplesPerRange < 1 || samplesPerRange > MAX_SAMPLES_PER_RANGE) {
            throw new BizException("每个网段抽样数必须在 1 到 " + MAX_SAMPLES_PER_RANGE + " 之间");
        }
        List<CfIpRange> ranges = rangeMapper.selectActive();
        long totalProbes = (long) ranges.size() * samplesPerRange;
        if (totalProbes == 0) {
            throw new BizException("请先初始化 Cloudflare 官方 IP 网段");
        }
        if (totalProbes > MAX_TOTAL_PROBES) {
            throw new BizException("本次抽样超过安全上限 " + MAX_TOTAL_PROBES + "，请减少每个网段的抽样数");
        }

        Set<String> existingIps = new HashSet<>(candidateMapper.selectAllIpAddresses());
        List<CfIpCandidate> candidates = new ArrayList<>((int) totalProbes);
        for (CfIpRange range : ranges) {
            CidrBlock block = CidrBlock.parse(range.getCidr());
            for (String ip : block.sampleAddresses(samplesPerRange, existingIps)) {
                existingIps.add(ip);
                CfIpCandidate candidate = new CfIpCandidate();
                candidate.setIpAddress(ip);
                candidate.setStatus("PENDING");
                candidate.setRegion(range.getRegion());
                candidate.setSourceRangeId(range.getId());
                candidates.add(candidate);
            }
        }

        List<CfIpCandidate> insertedCandidates = new ArrayList<>(candidates.size());
        for (CfIpCandidate candidate : candidates) {
            try {
                candidateMapper.insert(candidate);
                insertedCandidates.add(candidate);
            } catch (DuplicateKeyException e) {
                log.info("Skipped concurrently created sampled IP {}", candidate.getIpAddress());
            }
        }
        if (insertedCandidates.isEmpty()) {
            throw new BizException("抽样 IP 已被其他请求创建，请重试");
        }
        return probeService.probe(insertedCandidates, tlsServerName);
    }

    private List<CfIpRange> fetchOfficialRanges() {
        HttpRequest request = HttpRequest.newBuilder(CLOUDFLARE_IPS_URI)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "CrawlerOS-CfIpManagement/1.0")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("获取 Cloudflare 官方 IP 网段时被中断");
        } catch (IOException e) {
            log.warn("Could not fetch Cloudflare IP ranges", e);
            throw new BizException("无法获取 Cloudflare 官方 IP 网段，请检查服务网络");
        }
        if (response.statusCode() != 200) {
            throw new BizException("Cloudflare 官方 IP 网段接口返回 HTTP " + response.statusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                throw new BizException("Cloudflare 官方 IP 网段接口返回失败");
            }
            JsonNode result = root.path("result");
            List<CfIpRange> ranges = new ArrayList<>();
            readRanges(result.path("ipv4_cidrs"), ranges);
            readRanges(result.path("ipv6_cidrs"), ranges);
            return ranges;
        } catch (JacksonException e) {
            log.warn("Invalid Cloudflare IP ranges response", e);
            throw new BizException("Cloudflare 官方 IP 网段响应格式无效");
        }
    }

    private void readRanges(JsonNode cidrs, List<CfIpRange> ranges) {
        if (!cidrs.isArray()) {
            throw new BizException("Cloudflare 官方 IP 网段响应缺少 CIDR 列表");
        }
        for (JsonNode cidrNode : cidrs) {
            if (!cidrNode.isTextual()) {
                throw new BizException("Cloudflare 官方 IP 网段响应包含无效记录");
            }
            String cidr = cidrNode.asText();
            CidrBlock block = CidrBlock.parse(cidr);
            CfIpRange range = new CfIpRange();
            range.setCidr(cidr);
            range.setIpVersion(block.ipVersion());
            range.setActive(true);
            ranges.add(range);
        }
    }
}
