package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.entity.StockCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StockCatalogSyncClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset=([^;]+)");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${investment.stock-list-url:https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData}")
    private String listUrl;

    public List<StockCatalog> fetchCatalog(LocalDateTime syncedAt) {
        Map<String, StockCatalog> stocks = new LinkedHashMap<>();
        boolean lastPageReached = false;
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode rows = fetchPage(page);
            if (!rows.isArray()) {
                throw new BizException("股票目录服务返回了无法识别的数据");
            }
            for (JsonNode row : rows) {
                String code = row.path("code").asText();
                String name = row.path("name").asText();
                if (!code.matches("\\d{6}") || name.isBlank() || !isSupportedMarketCode(code)) {
                    continue;
                }
                StockCatalog stock = new StockCatalog();
                stock.setCode(code);
                stock.setName(name);
                stock.setMarket(marketName(row.path("symbol").asText(), code));
                stock.setIndustry("");
                stock.setSyncedAt(syncedAt);
                stocks.put(code, stock);
            }
            if (rows.size() < PAGE_SIZE) {
                lastPageReached = true;
                break;
            }
        }
        if (!lastPageReached) {
            throw new BizException("股票目录超过同步分页上限，未完成同步");
        }
        if (stocks.isEmpty()) {
            throw new BizException("股票目录服务没有返回有效的 A 股列表");
        }
        return new ArrayList<>(stocks.values());
    }

    private JsonNode fetchPage(int page) {
        String query = "?page=" + page
                + "&num=" + PAGE_SIZE
                + "&sort=symbol&asc=1&node=hs_a&symbol=";
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return fetchPage(listUrl, query);
            } catch (IOException e) {
                lastFailure = e;
                log.warn("Stock directory request failed via {} (attempt {}/{}): {}",
                        URI.create(listUrl).getHost(), attempt, MAX_ATTEMPTS, e.toString());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(300L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new BizException("股票目录同步被中断，请稍后重试");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException("股票目录同步被中断，请稍后重试");
            } catch (IllegalArgumentException e) {
                throw new BizException("股票目录服务地址无效");
            }
        }
        log.error("Could not fetch stock directory from configured Sina endpoint {}",
                URI.create(listUrl).getHost(), lastFailure);
        throw new BizException("股票目录服务连接失败，请检查服务器网络或配置的目录服务地址");
    }

    private JsonNode fetchPage(String baseUrl, String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + query))
                .timeout(Duration.ofSeconds(30))
                .header("Referer", "https://finance.sina.com.cn/")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json, */*")
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Sina HTTP " + response.statusCode() + " via "
                    + URI.create(baseUrl).getHost());
        }
        try {
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            Matcher charsetMatcher = CHARSET_PATTERN.matcher(contentType);
            Charset charset;
            try {
                charset = charsetMatcher.find()
                        ? Charset.forName(charsetMatcher.group(1).replace("\"", "").trim())
                        : StandardCharsets.UTF_8;
            } catch (IllegalArgumentException e) {
                throw new IOException("Unsupported response charset from " + URI.create(baseUrl).getHost(), e);
            }
            return OBJECT_MAPPER.readTree(new String(response.body(), charset));
        } catch (IOException e) {
            throw new IOException("Invalid JSON from " + URI.create(baseUrl).getHost(), e);
        }
    }

    private boolean isSupportedMarketCode(String code) {
        return code.startsWith("60") || code.startsWith("68")
                || code.startsWith("00") || code.startsWith("30")
                || code.startsWith("4") || code.startsWith("8")
                || code.startsWith("92");
    }

    private String marketName(String symbol, String code) {
        if (symbol.startsWith("bj") || code.startsWith("4") || code.startsWith("8") || code.startsWith("92")) {
            return "北京";
        }
        if (symbol.startsWith("sh") || code.startsWith("6")) {
            return "上海";
        }
        return "深圳";
    }
}
