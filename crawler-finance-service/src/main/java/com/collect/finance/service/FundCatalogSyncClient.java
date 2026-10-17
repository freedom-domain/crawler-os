package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.entity.FundCatalog;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FundCatalogSyncClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${investment.eastmoney-fund-catalog-url:https://fund.eastmoney.com/js/fundcode_search.js}")
    private String catalogUrl;

    public List<FundCatalog> fetchCatalog(LocalDateTime syncedAt) {
        String body = fetchBody();
        int start = body.indexOf('[');
        int end = body.lastIndexOf(']');
        if (start < 0 || end <= start) {
            log.error("Eastmoney fund catalog response did not contain a JSON array");
            throw new BizException("基金目录服务返回了无法识别的数据");
        }

        try {
            JsonNode rows = OBJECT_MAPPER.readTree(body.substring(start, end + 1));
            if (!rows.isArray() || rows.isEmpty()) {
                throw new BizException("基金目录服务没有返回可用的基金列表");
            }
            Map<String, FundCatalog> unique = new LinkedHashMap<>();
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 4) {
                    continue;
                }
                String code = row.get(0).asText();
                String name = row.get(2).asText();
                if (!code.matches("\\d{6}") || name.isBlank()) {
                    continue;
                }
                FundCatalog item = new FundCatalog();
                item.setCode(code);
                item.setFundType(isOnshoreCode(code) ? "ONSHORE" : "OFFSHORE");
                item.setName(name);
                item.setCategory(row.get(3).asText(""));
                item.setPinyin(row.size() > 4 ? row.get(4).asText("") : row.get(1).asText(""));
                item.setSyncedAt(syncedAt);
                unique.put(item.getFundType() + ':' + code, item);
            }
            if (unique.isEmpty()) {
                throw new BizException("基金目录服务没有返回有效的基金信息");
            }
            return new ArrayList<>(unique.values());
        } catch (IOException e) {
            log.error("Could not parse Eastmoney fund catalog response", e);
            throw new BizException("基金目录服务返回了无效数据");
        }
    }

    private String fetchBody() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(catalogUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("Referer", "https://fund.eastmoney.com/")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/javascript, text/javascript, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("基金目录服务暂不可用，HTTP " + response.statusCode());
            }
            return response.body().replaceFirst("^\\uFEFF", "");
        } catch (IOException e) {
            log.warn("Eastmoney fund catalog request failed: {}", e.getMessage());
            throw new BizException("连接基金目录服务失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("基金目录同步被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("基金目录服务地址无效");
        }
    }

    private boolean isOnshoreCode(String code) {
        return code.matches("(5\\d{5}|15\\d{4}|16\\d{4}|18\\d{4})");
    }
}
