package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.config.TencentHistoryRequestLimiter;
import com.collect.finance.dto.StockHistoryPoint;
import com.collect.finance.dto.StockHistoryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.TreeMap;

@Slf4j
@Component
public class TencentStockHistoryClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StockHistoryCacheService historyCacheService;
    private final TencentHistoryRequestLimiter requestLimiter;

    @Value("${investment.tencent-history-url:https://web.ifzq.gtimg.cn/appstock/app/fqkline/get}")
    private String historyUrl;

    public TencentStockHistoryClient(StockHistoryCacheService historyCacheService,
                                     TencentHistoryRequestLimiter requestLimiter) {
        this.historyCacheService = historyCacheService;
        this.requestLimiter = requestLimiter;
    }

    public StockHistoryResponse getHistory(String code, LocalDate startDate, LocalDate endDate) {
        String dataKey = marketCode(code) + code;
        TreeMap<LocalDate, StockHistoryPoint> pointMap = new TreeMap<>();
        String name = code;
        for (LocalDate chunkStart = startDate; !chunkStart.isAfter(endDate); ) {
            LocalDate chunkEnd = chunkStart.plusDays(299);
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate;
            }
            JsonNode root = fetch(dataKey, chunkStart, chunkEnd);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException("历史行情服务返回错误：" + root.path("msg").asText("未知错误"));
            }
            JsonNode data = root.path("data").path(dataKey);
            if (data.isMissingNode() || data.isNull()) {
                throw new BizException("历史行情服务未找到股票代码 " + code + "，请确认代码正确且股票已上市");
            }
            JsonNode quote = data.path("qt").path(dataKey);
            if (quote.isArray() && quote.size() > 1 && !quote.get(1).asText().isBlank()) {
                name = quote.get(1).asText();
            }
            JsonNode rows = data.path("qfqday");
            if (!rows.isArray()) {
                throw new BizException("历史行情服务没有返回可用的价格序列");
            }
            for (JsonNode row : rows) {
                StockHistoryPoint point = parsePoint(row, code);
                if (point != null) {
                    LocalDate date = LocalDate.parse(point.getDate());
                    if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                        pointMap.put(date, point);
                    }
                }
            }
            chunkStart = chunkEnd.plusDays(1);
        }

        ArrayList<StockHistoryPoint> points = new ArrayList<>(pointMap.values());
        BigDecimal previousClose = null;
        for (StockHistoryPoint point : points) {
            BigDecimal changePercent = previousClose == null || previousClose.signum() == 0
                    ? BigDecimal.ZERO
                    : point.getClose().subtract(previousClose).multiply(BigDecimal.valueOf(100))
                            .divide(previousClose, 2, java.math.RoundingMode.HALF_UP);
            point.setChangePercent(changePercent);
            previousClose = point.getClose();
        }

        StockHistoryResponse response = new StockHistoryResponse();
        response.setCode(code);
        response.setName(name);
        response.setFrequency("day");
        response.setPoints(points);
        historyCacheService.save(code, name, points);
        return response;
    }

    private StockHistoryPoint parsePoint(JsonNode row, String code) {
        if (!row.isArray() || row.size() < 6) {
            return null;
        }
        try {
            StockHistoryPoint point = new StockHistoryPoint();
            point.setDate(row.get(0).asText());
            point.setOpen(new BigDecimal(row.get(1).asText()));
            point.setClose(new BigDecimal(row.get(2).asText()));
            point.setHigh(new BigDecimal(row.get(3).asText()));
            point.setLow(new BigDecimal(row.get(4).asText()));
            point.setVolume(new BigDecimal(row.get(5).asText()));
            LocalDate.parse(point.getDate());
            if (point.getClose().signum() <= 0) {
                log.warn("Skipping Tencent stock history row with a non-positive close for {}", code);
                return null;
            }
            return point;
        } catch (NumberFormatException | DateTimeParseException e) {
            log.warn("Skipping invalid Tencent stock history row for {}: {}", code, e.getMessage());
            return null;
        }
    }

    private JsonNode fetch(String dataKey, LocalDate startDate, LocalDate endDate) {
        boolean acquired = false;
        try {
            requestLimiter.acquire();
            acquired = true;
            String params = dataKey + ",day," + startDate + "," + endDate + ",320,qfq";
            String url = historyUrl + "?param=" + URLEncoder.encode(params, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Referer", "https://gu.qq.com/")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("历史行情服务暂不可用，HTTP " + response.statusCode());
            }
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            if (root == null || !root.isObject()) {
                throw new BizException("历史行情服务返回了无效数据");
            }
            return root;
        } catch (IOException e) {
            log.warn("Tencent stock history request failed: {}", e.getMessage());
            throw new BizException("历史行情服务连接失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("历史行情查询被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("历史行情服务地址或股票代码无效");
        } finally {
            if (acquired) {
                requestLimiter.release();
            }
        }
    }

    private String marketCode(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new BizException("股票代码必须为 6 位数字");
        }
        char first = code.charAt(0);
        if (first == '6') {
            return "sh";
        }
        if (first == '0' || first == '1' || first == '2' || first == '3') {
            return "sz";
        }
        if (first == '4' || first == '8' || first == '9') {
            return "bj";
        }
        throw new BizException("暂不支持该证券市场的代码");
    }
}
