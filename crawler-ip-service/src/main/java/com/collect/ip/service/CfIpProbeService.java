package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.mapper.CfIpCandidateMapper;
import com.collect.ip.mapper.CfIpRangeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CfIpProbeService {

    private static final int CONNECT_TIMEOUT_MILLIS = 2_500;
    private static final int SOCKET_READ_TIMEOUT_MILLIS = 5_000;
    private static final int TCP_PROBE_TIMEOUT_MILLIS = 2_500;
    private static final int DOWNLOAD_BYTES = 30_000_000;
    private static final int MAX_RESPONSE_BYTES = 32_000_000;
    private static final int MAX_CONCURRENT_PROBES = 1;
    private static final String SPEED_TEST_HOST = "speed.cloudflare.com";
    private static final Pattern CF_RAY_PATTERN = Pattern.compile("^[0-9a-fA-F]+-([A-Z]{3})$",
            Pattern.CASE_INSENSITIVE);

    private final CfIpCandidateMapper mapper;
    private final CfIpRangeMapper rangeMapper;

    public CfIpProbeService(CfIpCandidateMapper mapper, CfIpRangeMapper rangeMapper) {
        this.mapper = mapper;
        this.rangeMapper = rangeMapper;
    }

    public List<CfIpCandidate> probe(List<CfIpCandidate> candidates, String tlsServerName) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        String normalizedServerName = normalizeServerName(tlsServerName);

        try (var executor = Executors.newFixedThreadPool(Math.min(MAX_CONCURRENT_PROBES, candidates.size()))) {
            List<Future<CfIpCandidate>> probes = new ArrayList<>(candidates.size());
            for (CfIpCandidate candidate : candidates) {
                probes.add(executor.submit(() -> probeOne(candidate, normalizedServerName)));
            }
            List<CfIpCandidate> results = new ArrayList<>(probes.size());
            for (Future<CfIpCandidate> probe : probes) {
                try {
                    results.add(probe.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("IP 测试被中断");
                } catch (ExecutionException e) {
                    log.error("Cloudflare IP candidate probe failed", e.getCause());
                    throw new BizException("IP 测试结果写入失败，请检查服务日志");
                }
            }
            Set<Long> updatedRangeIds = results.stream()
                    .filter(candidate -> candidate.getSourceRangeId() != null && candidate.getColoUpdatedAt() != null)
                    .map(candidate -> candidate.getSourceRangeId())
                    .collect(java.util.stream.Collectors.toSet());
            updatedRangeIds.forEach(rangeMapper::refreshColoFromCandidateObservations);
            return results;
        }
    }

    private CfIpCandidate probeOne(CfIpCandidate candidate, String tlsServerName) {
        candidate.setPingMs(null);
        candidate.setTlsMs(null);
        candidate.setSpeedMbps(null);
        candidate.setLatencyMs(null);
        candidate.setTlsServerName(tlsServerName);
        candidate.setLastTestedAt(LocalDateTime.now());
        List<String> errors = new ArrayList<>();

        InetAddress address = CfIpCandidateService.parsePublicAddress(candidate.getIpAddress());
        try (Socket tcp = new Socket()) {
            long pingStarted = System.nanoTime();
            tcp.connect(new InetSocketAddress(address, 443), TCP_PROBE_TIMEOUT_MILLIS);
            candidate.setPingMs(toMillis(System.nanoTime() - pingStarted));
        } catch (IOException e) {
            errors.add("TCP 443 连接失败：" + safeMessage(e));
        }

        try {
            testTlsHandshake(candidate, address, tlsServerName);
        } catch (IOException e) {
            errors.add("TLS 握手失败：" + safeMessage(e));
        }
        try {
            testDownloadSpeed(candidate, address);
        } catch (IOException e) {
            errors.add("测速失败：" + safeMessage(e));
        }

        candidate.setStatus(candidate.getTlsMs() != null ? "AVAILABLE" : "FAILED");
        candidate.setErrorMessage(errors.isEmpty() ? null : String.join("；", errors));
        if (candidate.getTlsMs() != null) {
            candidate.setLatencyMs(candidate.getTlsMs().setScale(0, RoundingMode.HALF_UP).intValue());
        }
        if (mapper.updateProbeResult(candidate) == 0) {
            throw new BizException("IP 候选已被删除：" + candidate.getIpAddress());
        }
        return candidate;
    }

    private void testTlsHandshake(CfIpCandidate candidate, InetAddress address, String serverName) throws IOException {
        try (Socket tcp = new Socket()) {
            tcp.connect(new InetSocketAddress(address, 443), CONNECT_TIMEOUT_MILLIS);
            tcp.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket tls = (SSLSocket) factory.createSocket(tcp, serverName, 443, true)) {
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName(serverName)));
                parameters.setApplicationProtocols(new String[]{"http/1.1"});
                tls.setSSLParameters(parameters);
                long tlsStarted = System.nanoTime();
                tls.startHandshake();
                candidate.setTlsMs(toMillis(System.nanoTime() - tlsStarted));
            }
        }
    }

    private String normalizeServerName(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            throw new BizException("请输入与 v2rayN 节点一致的 TLS serverName/SNI");
        }
        String normalized;
        try {
            normalized = IDN.toASCII(serverName.trim(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new BizException("TLS serverName/SNI 格式无效");
        }
        if (normalized.length() > 253 || normalized.isBlank()) {
            throw new BizException("TLS serverName/SNI 格式无效");
        }
        return normalized;
    }

    private void testDownloadSpeed(CfIpCandidate candidate, InetAddress address) throws IOException {
        try (Socket tcp = new Socket()) {
            tcp.connect(new InetSocketAddress(address, 443), CONNECT_TIMEOUT_MILLIS);
            tcp.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket tls = (SSLSocket) factory.createSocket(tcp, SPEED_TEST_HOST, 443, true)) {
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName(SPEED_TEST_HOST)));
                parameters.setApplicationProtocols(new String[]{"http/1.1"});
                tls.setSSLParameters(parameters);
                tls.startHandshake();
                long speedStarted = System.nanoTime();
                DownloadResult downloadResult = downloadTestBody(tls);
                long elapsedNanos = System.nanoTime() - speedStarted;
                if (downloadResult.coloCode() != null) {
                    candidate.setColo(downloadResult.coloCode());
                    candidate.setColoUpdatedAt(LocalDateTime.now());
                }
                long bytesReceived = downloadResult.bytesReceived();
                if (bytesReceived > 0 && elapsedNanos > 0) {
                    double mbps = bytesReceived * 8.0 / elapsedNanos * 1_000.0;
                    candidate.setSpeedMbps(BigDecimal.valueOf(mbps).setScale(3, RoundingMode.HALF_UP));
                } else {
                    throw new IOException("下载响应为空");
                }
            }
        }
    }

    private DownloadResult downloadTestBody(SSLSocket socket) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write(("GET /__down?bytes=" + DOWNLOAD_BYTES + " HTTP/1.1\r\n"
                + "Host: " + SPEED_TEST_HOST + "\r\n"
                + "User-Agent: crawler-os-cf-ip-probe\r\n"
                + "Accept: */*\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();

        InputStream input = new BufferedInputStream(socket.getInputStream());
        String statusLine = readHttpLine(input);
        if (statusLine == null || !statusLine.matches("HTTP/1\\.[01] 2\\d\\d(?: .*)?")) {
            throw new IOException("测速服务返回异常响应");
        }

        Map<String, String> headers = new java.util.HashMap<>();
        String line;
        while ((line = readHttpLine(input)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        String coloCode = coloCodeFromCfRay(headers.get("cf-ray"));
        String transferEncoding = headers.getOrDefault("transfer-encoding", "");
        if (transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return new DownloadResult(readChunkedBody(input), coloCode);
        }
        String contentLength = headers.get("content-length");
        if (contentLength != null) {
            long length;
            try {
                length = Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                throw new IOException("测速响应长度无效", e);
            }
            if (length < 0 || length > MAX_RESPONSE_BYTES) {
                throw new IOException("测速响应长度超出限制");
            }
            return new DownloadResult(readExactBody(input, length), coloCode);
        }
        return new DownloadResult(readUntilEnd(input, MAX_RESPONSE_BYTES), coloCode);
    }

    static String coloCodeFromCfRay(String cfRay) {
        if (cfRay == null) {
            return null;
        }
        Matcher matcher = CF_RAY_PATTERN.matcher(cfRay.trim());
        return matcher.matches() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private long readChunkedBody(InputStream input) throws IOException {
        long total = 0;
        while (true) {
            String sizeLine = readHttpLine(input);
            if (sizeLine == null) {
                throw new IOException("测速响应分块不完整");
            }
            int extension = sizeLine.indexOf(';');
            String sizeText = extension >= 0 ? sizeLine.substring(0, extension) : sizeLine;
            long size;
            try {
                size = Long.parseLong(sizeText.trim(), 16);
            } catch (NumberFormatException e) {
                throw new IOException("测速响应分块长度无效", e);
            }
            if (size == 0) {
                String trailer;
                while ((trailer = readHttpLine(input)) != null && !trailer.isEmpty()) {
                }
                return total;
            }
            if (size < 0 || total + size > MAX_RESPONSE_BYTES) {
                throw new IOException("测速响应超出限制");
            }
            total += readExactBody(input, size);
            if (input.read() != '\r' || input.read() != '\n') {
                throw new IOException("测速响应分块格式无效");
            }
        }
    }

    private long readExactBody(InputStream input, long length) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8_192];
        while (total < length) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, length - total));
            if (read < 0) {
                throw new IOException("测速响应内容不完整");
            }
            total += read;
        }
        return total;
    }

    private long readUntilEnd(InputStream input, long limit) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8_192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new IOException("测速响应超出限制");
            }
        }
        return total;
    }

    private String readHttpLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) >= 0) {
            if (value == '\n') {
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                    line.setLength(line.length() - 1);
                }
                return line.toString();
            }
            line.append((char) value);
            if (line.length() > 8_192) {
                throw new IOException("测速响应头过长");
            }
        }
        return line.isEmpty() ? null : line.toString();
    }

    static BigDecimal toMillisValue(long elapsedNanos) {
        return BigDecimal.valueOf(elapsedNanos)
                .divide(BigDecimal.valueOf(1_000_000), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal toMillis(long elapsedNanos) {
        return toMillisValue(elapsedNanos);
    }

    private String safeMessage(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "连接失败" : message;
    }

    private record DownloadResult(long bytesReceived, String coloCode) {
    }
}
