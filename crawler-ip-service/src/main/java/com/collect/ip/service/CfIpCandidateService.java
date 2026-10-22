package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.mapper.CfIpCandidateMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Service
public class CfIpCandidateService {

    private static final int MAX_IMPORT_COUNT = 1_000;
    private static final int MAX_PROBE_COUNT = 100;
    private final CfIpCandidateMapper mapper;
    private final CfIpProbeService probeService;

    public CfIpCandidateService(CfIpCandidateMapper mapper, CfIpProbeService probeService) {
        this.mapper = mapper;
        this.probeService = probeService;
    }

    public List<CfIpCandidate> list() {
        return mapper.selectAllOrdered();
    }

    public int addCandidates(String rawIps) {
        List<String> submittedIps = Arrays.stream(rawIps.split("[\\s,;]+"))
                .map(ip -> ip.trim())
                .filter(ip -> !ip.isEmpty())
                .distinct()
                .toList();
        if (submittedIps.isEmpty()) {
            throw new BizException("请至少输入一个 IP 地址");
        }
        if (submittedIps.size() > MAX_IMPORT_COUNT) {
            throw new BizException("单次最多导入 " + MAX_IMPORT_COUNT + " 个 IP 地址");
        }
        List<String> ips = submittedIps.stream()
                .map(ip -> parsePublicAddress(ip).getHostAddress())
                .distinct()
                .toList();

        int added = 0;
        for (String ip : ips) {
            CfIpCandidate candidate = new CfIpCandidate();
            candidate.setIpAddress(ip);
            candidate.setRegion("全球任播");
            candidate.setStatus("PENDING");
            try {
                mapper.insert(candidate);
                added++;
            } catch (DuplicateKeyException ignored) {
                // Re-importing a candidate keeps its previous test result.
            }
        }
        return added;
    }

    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException("IP 候选记录不存在");
        }
    }

    @Transactional
    public int deleteCandidates(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择需要删除的 IP");
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        if (distinctIds.size() > MAX_IMPORT_COUNT) {
            throw new BizException("单次最多删除 " + MAX_IMPORT_COUNT + " 个 IP");
        }
        if (mapper.selectByIds(distinctIds).size() != distinctIds.size()) {
            throw new BizException("部分 IP 候选记录已不存在，请刷新列表后重试");
        }
        int deleted = mapper.deleteByIds(distinctIds);
        if (deleted != distinctIds.size()) {
            throw new BizException("部分 IP 候选记录删除失败，请刷新列表后重试");
        }
        return deleted;
    }

    public List<CfIpCandidate> probe(List<Long> ids, String tlsServerName) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择需要测速的 IP");
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        if (distinctIds.size() > MAX_PROBE_COUNT) {
            throw new BizException("单次最多测速 " + MAX_PROBE_COUNT + " 个 IP");
        }
        List<CfIpCandidate> candidates = mapper.selectByIds(distinctIds);
        if (candidates.size() != distinctIds.size()) {
            throw new BizException("部分 IP 候选记录已不存在，请刷新列表后重试");
        }

        return probeService.probe(candidates, tlsServerName);
    }

    static InetAddress parsePublicAddress(String ip) {
        try {
            InetAddress address;
            if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                String[] parts = ip.split("\\.");
                byte[] bytes = new byte[4];
                for (int i = 0; i < parts.length; i++) {
                    int octet = Integer.parseInt(parts[i]);
                    if (octet > 255) {
                        throw new BizException("无效的 IP 地址：" + ip);
                    }
                    bytes[i] = (byte) octet;
                }
                address = InetAddress.getByAddress(bytes);
            } else if (ip.length() <= 45 && ip.contains(":") && ip.matches("[0-9a-fA-F:]+")) {
                address = InetAddress.getByName(ip);
            } else {
                throw new BizException("无效的 IP 地址：" + ip);
            }
            if (!isPublicAddress(address)) {
                throw new BizException("仅支持公网 IP 地址：" + ip);
            }
            return address;
        } catch (IOException | NumberFormatException e) {
            throw new BizException("无效的 IP 地址：" + ip);
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            byte[] octets = address.getAddress();
            int first = Byte.toUnsignedInt(octets[0]);
            int second = Byte.toUnsignedInt(octets[1]);
            int third = Byte.toUnsignedInt(octets[2]);
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && (second == 0 || second == 168))
                    && !(first == 192 && second == 88 && third == 99)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113);
        }
        if (address instanceof Inet6Address) {
            byte[] octets = address.getAddress();
            boolean globalUnicast = (Byte.toUnsignedInt(octets[0]) & 0xE0) == 0x20;
            boolean documentation = Byte.toUnsignedInt(octets[0]) == 0x20
                    && Byte.toUnsignedInt(octets[1]) == 0x01
                    && Byte.toUnsignedInt(octets[2]) == 0x0D
                    && Byte.toUnsignedInt(octets[3]) == 0xB8;
            return globalUnicast && !documentation;
        }
        return false;
    }
}
