package com.collect.ip.service;

import com.collect.common.exception.BizException;

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

record CidrBlock(String cidr, int ipVersion, BigInteger network, int prefixLength, int addressBits) {

    static CidrBlock parse(String cidr) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) {
            throw new BizException("无效的 CIDR 网段：" + cidr);
        }
        InetAddress address = CfIpCandidateService.parsePublicAddress(parts[0]);
        int addressBits = address instanceof Inet6Address ? 128 : 32;
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new BizException("无效的 CIDR 前缀：" + cidr);
        }
        if (prefix < 0 || prefix > addressBits) {
            throw new BizException("无效的 CIDR 前缀：" + cidr);
        }
        BigInteger ip = new BigInteger(1, address.getAddress());
        BigInteger mask = BigInteger.ONE.shiftLeft(addressBits).subtract(BigInteger.ONE)
                .shiftRight(addressBits - prefix).shiftLeft(addressBits - prefix);
        BigInteger network = ip.and(mask);
        if (!network.equals(ip)) {
            throw new BizException("CIDR 地址不是网段起始地址：" + cidr);
        }
        return new CidrBlock(cidr, addressBits == 32 ? 4 : 6, network, prefix, addressBits);
    }

    String sampleAddress(int sampleIndex) {
        return sampleAddress(BigInteger.valueOf(sampleIndex));
    }

    List<String> sampleAddresses(int sampleCount, Set<String> excludedAddresses) {
        List<String> samples = new ArrayList<>(sampleCount);
        BigInteger sampleIndex = BigInteger.ZERO;
        BigInteger lastOffset = lastUsableOffset();
        while (samples.size() < sampleCount && sampleIndex.compareTo(lastOffset) < 0) {
            String address = sampleAddress(sampleIndex);
            if (!excludedAddresses.contains(address)) {
                samples.add(address);
            }
            sampleIndex = sampleIndex.add(BigInteger.ONE);
        }
        if (samples.size() < sampleCount) {
            throw new BizException("该网段未抽样的可用 IP 地址少于请求的抽样数：" + cidr);
        }
        return samples;
    }

    private String sampleAddress(BigInteger sampleIndex) {
        if (sampleIndex.signum() < 0) {
            throw new BizException("抽样序号不能为负数：" + cidr);
        }
        BigInteger offset = sampleIndex.add(BigInteger.ONE);
        if (offset.compareTo(lastUsableOffset()) > 0) {
            throw new BizException("该网段可用 IP 地址少于请求的抽样数：" + cidr);
        }
        return formatAddress(offset);
    }

    BigInteger firstUsableOffset() {
        return ipVersion == 4 && prefixLength <= 30 ? BigInteger.ONE : BigInteger.ZERO;
    }

    String addressAtOffset(BigInteger offset) {
        if (offset.compareTo(firstUsableOffset()) < 0 || offset.compareTo(lastUsableOffset()) > 0) {
            throw new BizException("IP 地址偏移超出网段范围：" + cidr);
        }
        return formatAddress(offset);
    }

    private String formatAddress(BigInteger offset) {
        BigInteger value = network.add(offset);
        byte[] encoded = value.toByteArray();
        byte[] addressBytes = new byte[addressBits / 8];
        int copyLength = Math.min(encoded.length, addressBytes.length);
        System.arraycopy(encoded, encoded.length - copyLength, addressBytes, addressBytes.length - copyLength, copyLength);
        try {
            return InetAddress.getByAddress(addressBytes).getHostAddress();
        } catch (UnknownHostException e) {
            throw new BizException("无法生成网段抽样 IP：" + cidr);
        }
    }

    BigInteger lastUsableOffset() {
        int hostBits = addressBits - prefixLength;
        BigInteger addressCount = BigInteger.ONE.shiftLeft(hostBits);
        if (ipVersion == 4 && prefixLength <= 30) {
            return addressCount.subtract(BigInteger.TWO);
        }
        return addressCount.subtract(BigInteger.ONE);
    }
}
