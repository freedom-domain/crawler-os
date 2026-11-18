package com.collect.ip.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.math.BigInteger;
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
    private static final int CANDIDATE_INSERT_BATCH_SIZE = 500;
    private static final int SAMPLE_LOOKUP_BATCH_SIZE = 100;

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

    public IPage<CfIpRange> page(int current, int size, String cidr, Integer ipVersion, String region, String colo) {
        if (ipVersion != null && ipVersion != 4 && ipVersion != 6) {
            throw new BizException("IP 版本只能是 4 或 6");
        }
        return rangeMapper.selectActiveRangePage(
                new Page<>(Math.max(1, current), Math.min(Math.max(1, size), 100)),
                blankToNull(cidr), ipVersion, blankToNull(region), blankToNull(colo));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public CfIpRange updateEnabled(Long id, boolean enabled) {
        CfIpRange range = rangeMapper.selectById(id);
        if (range == null || !Boolean.TRUE.equals(range.getActive())) {
            throw new BizException("Cloudflare 网段不存在，请刷新后重试");
        }
        range.setEnabled(enabled);
        rangeMapper.updateById(range);
        return rangeMapper.selectById(id);
    }

    public int importEnabledIpv4Addresses() {
        List<CfIpRange> ranges = rangeMapper.selectEnabled().stream()
                .filter(range -> Integer.valueOf(4).equals(range.getIpVersion()))
                .toList();
        if (ranges.isEmpty()) {
            throw new BizException("没有启用的 Cloudflare IPv4 网段");
        }

        int inserted = 0;
        List<CfIpCandidate> batch = new ArrayList<>(CANDIDATE_INSERT_BATCH_SIZE);
        for (CfIpRange range : ranges) {
            CidrBlock block = CidrBlock.parse(range.getCidr());
            if (block.ipVersion() != 4) {
                throw new BizException("Cloudflare 网段版本与 CIDR 不匹配：" + range.getCidr());
            }
            for (BigInteger offset = block.firstUsableOffset();
                 offset.compareTo(block.lastUsableOffset()) <= 0;
                 offset = offset.add(BigInteger.ONE)) {
                CfIpCandidate candidate = new CfIpCandidate();
                candidate.setIpAddress(block.addressAtOffset(offset));
                candidate.setRegion(range.getRegion() == null || range.getRegion().isBlank()
                        ? "全球任播" : range.getRegion());
                candidate.setSourceRangeId(range.getId());
                batch.add(candidate);
                if (batch.size() == CANDIDATE_INSERT_BATCH_SIZE) {
                    inserted += candidateMapper.insertIgnoreBatch(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            inserted += candidateMapper.insertIgnoreBatch(batch);
        }
        return inserted;
    }

    public List<CfIpCandidate> probeSamples(int samplesPerRange, String tlsServerName) {
        if (samplesPerRange < 1 || samplesPerRange > MAX_SAMPLES_PER_RANGE) {
            throw new BizException("每个网段抽样数必须在 1 到 " + MAX_SAMPLES_PER_RANGE + " 之间");
        }
        List<CfIpRange> ranges = rangeMapper.selectEnabled();
        long totalProbes = (long) ranges.size() * samplesPerRange;
        if (totalProbes == 0) {
            throw new BizException("没有启用的 Cloudflare IP 网段，请先启用至少一个网段");
        }
        if (totalProbes > MAX_TOTAL_PROBES) {
            throw new BizException("本次抽样超过安全上限 " + MAX_TOTAL_PROBES + "，请减少每个网段的抽样数");
        }

        Set<String> sampledIps = new HashSet<>();
        List<CfIpCandidate> candidates = new ArrayList<>((int) totalProbes);
        List<CfIpCandidate> newCandidates = new ArrayList<>((int) totalProbes);
        for (CfIpRange range : ranges) {
            CidrBlock block = CidrBlock.parse(range.getCidr());
            List<CfIpCandidate> untested = candidateMapper.selectUntestedBySourceRangeId(
                    range.getId(), samplesPerRange);
            candidates.addAll(untested);
            for (CfIpCandidate candidate : untested) {
                sampledIps.add(candidate.getIpAddress());
            }

            int remainingSamples = samplesPerRange - untested.size();
            if (remainingSamples == 0) {
                continue;
            }
            BigInteger offset = block.firstUsableOffset();
            while (remainingSamples > 0 && offset.compareTo(block.lastUsableOffset()) <= 0) {
                List<String> lookupIps = new ArrayList<>(SAMPLE_LOOKUP_BATCH_SIZE);
                while (lookupIps.size() < SAMPLE_LOOKUP_BATCH_SIZE
                        && offset.compareTo(block.lastUsableOffset()) <= 0) {
                    lookupIps.add(block.addressAtOffset(offset));
                    offset = offset.add(BigInteger.ONE);
                }
                Set<String> existingIps = new HashSet<>(candidateMapper.selectExistingIpAddresses(lookupIps));
                for (String ip : lookupIps) {
                    if (remainingSamples == 0) {
                        break;
                    }
                    if (existingIps.contains(ip) || !sampledIps.add(ip)) {
                        continue;
                    }
                    CfIpCandidate candidate = new CfIpCandidate();
                    candidate.setIpAddress(ip);
                    candidate.setStatus("PENDING");
                    candidate.setRegion(range.getRegion());
                    candidate.setSourceRangeId(range.getId());
                    newCandidates.add(candidate);
                    remainingSamples--;
                }
            }
        }

        for (CfIpCandidate candidate : newCandidates) {
            try {
                candidateMapper.insert(candidate);
                candidates.add(candidate);
            } catch (DuplicateKeyException e) {
                log.info("Skipped concurrently created sampled IP {}", candidate.getIpAddress());
            }
        }
        if (candidates.isEmpty()) {
            throw new BizException("没有可抽样的 IP 地址，请检查启用网段");
        }
        return probeService.probe(candidates, tlsServerName);
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
