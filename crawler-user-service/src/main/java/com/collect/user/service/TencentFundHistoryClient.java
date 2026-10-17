package com.collect.user.service;

import com.collect.common.exception.BizException;
import com.collect.user.dto.FundHistoryPoint;
import com.collect.user.dto.FundHistoryResponse;
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
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TencentFundHistoryClient {

    private static final Pattern FUND_CODE = Pattern.compile("(5\\d{5}|15\\d{4}|16\\d{4}|18\\d{4})");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FundHistoryCacheService historyCacheService;

    @Value("${investment.tencent-history-url:https://web.ifzq.gtimg.cn/appstock/app/fqkline/get}")
    private String historyUrl;

    public TencentFundHistoryClient(FundHistoryCacheService historyCacheService) {
        this.historyCacheService = historyCacheService;
    }

    public FundHistoryResponse getHistory(String code, LocalDate startDate, LocalDate endDate, String frequency) {
        String interval = switch (frequency) {
            case "day" -> "day";
            case "week" -> "week";
            case "month" -> "month";
            default -> throw new BizException("行情周期仅支持日、周、月");
        };
        String marketCode = marketCode(code);
        String dataKey = marketCode + code;
        int calendarDaysPerRequest = switch (frequency) {
            case "day" -> 300;
            case "week" -> 2100;
            case "month" -> 3650;
            default -> throw new BizException("行情周期仅支持日、周、月");
        };
        TreeMap<LocalDate, FundHistoryPoint> pointMap = new TreeMap<>();
        String name = code;
        for (LocalDate chunkStart = startDate; !chunkStart.isAfter(endDate); ) {
            LocalDate chunkEnd = chunkStart.plusDays(calendarDaysPerRequest - 1L);
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate;
            }
            JsonNode root = fetch(dataKey, interval, chunkStart, chunkEnd);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException("历史行情服务返回错误：" + root.path("msg").asText("未知错误"));
            }
            JsonNode data = root.path("data").path(dataKey);
            if (data.isMissingNode() || data.isNull()) {
                throw new BizException("历史行情服务未找到基金代码 " + code + "，请确认代码正确且为场内基金");
            }
            JsonNode nameNode = data.path("qt").path(dataKey);
            if (nameNode.isArray() && nameNode.size() > 1 && !nameNode.get(1).asText().isBlank()) {
                name = nameNode.get(1).asText();
            }
            JsonNode rows = data.path("qfq" + interval);
            if (!rows.isArray()) {
                throw new BizException("历史行情服务没有返回可用的价格序列");
            }
            for (JsonNode row : rows) {
                FundHistoryPoint point = parsePoint(row, code);
                if (point != null) {
                    LocalDate date = LocalDate.parse(point.getDate());
                    if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                        pointMap.put(date, point);
                    }
                }
            }
            chunkStart = chunkEnd.plusDays(1);
        }

        ArrayList<FundHistoryPoint> points = new ArrayList<>(pointMap.values());
        BigDecimal previousClose = null;
        for (FundHistoryPoint point : points) {
            BigDecimal changePercent = previousClose == null || previousClose.signum() == 0
                    ? BigDecimal.ZERO
                    : point.getClose().subtract(previousClose).multiply(BigDecimal.valueOf(100))
                            .divide(previousClose, 2, java.math.RoundingMode.HALF_UP);
            point.setChangePercent(changePercent);
            previousClose = point.getClose();
        }

        FundHistoryResponse response = new FundHistoryResponse();
        response.setCode(code);
        response.setName(name);
        response.setFrequency(frequency);
        response.setPoints(points);
        response.setAnalysis(FundHistoryAnalyzer.analyzeOnExchange(points, frequency));
        historyCacheService.saveOnExchange(code, name, frequency, points);
        return response;
    }

    private FundHistoryPoint parsePoint(JsonNode row, String code) {
        if (!row.isArray() || row.size() < 6) {
            return null;
        }
        try {
            FundHistoryPoint point = new FundHistoryPoint();
            point.setDate(row.get(0).asText());
            point.setOpen(new BigDecimal(row.get(1).asText()));
            point.setClose(new BigDecimal(row.get(2).asText()));
            point.setHigh(new BigDecimal(row.get(3).asText()));
            point.setLow(new BigDecimal(row.get(4).asText()));
            point.setVolume(new BigDecimal(row.get(5).asText()));
            return point;
        } catch (NumberFormatException e) {
            log.warn("Skipping invalid Tencent history row for {}: {}", code, e.getMessage());
            return null;
        }
    }

    private JsonNode fetch(String dataKey, String interval, LocalDate startDate, LocalDate endDate) {
        try {
            String params = dataKey + "," + interval + "," + startDate + "," + endDate + ",320,qfq";
            String url = historyUrl + "?param=" + URLEncoder.encode(params, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Referer", "https://gu.qq.com/")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("历史行情服务暂不可用，HTTP " + response.statusCode());
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (IOException e) {
            log.warn("Tencent history request failed: {}", e.getMessage());
            throw new BizException("历史行情服务连接失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("历史行情查询被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("历史行情服务地址无效");
        }
    }

    private String marketCode(String code) {
        if (code == null || !FUND_CODE.matcher(code).matches()) {
            throw new BizException("历史行情仅支持 6 位场内基金代码（如 510300、159915）");
        }
        return code.startsWith("5") ? "sh" : "sz";
    }
}
