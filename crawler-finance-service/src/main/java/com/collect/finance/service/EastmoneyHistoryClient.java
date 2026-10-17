package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.config.HistoryRequestLimiter;
import com.collect.finance.dto.FundHistoryPoint;
import com.collect.finance.dto.FundHistoryResponse;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Slf4j
@Component
public class EastmoneyHistoryClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final HistoryRequestLimiter requestLimiter;
    private final StockHistoryCacheService stockHistoryCache;
    private final FundHistoryCacheService fundHistoryCache;

    @Value("${investment.eastmoney-history-url:https://push2his.eastmoney.com/api/qt/stock/kline/get}")
    private String eastmoneyHistoryUrl;

    public EastmoneyHistoryClient(HistoryRequestLimiter requestLimiter,
                                  StockHistoryCacheService stockHistoryCache,
                                  FundHistoryCacheService fundHistoryCache) {
        this.requestLimiter = requestLimiter;
        this.stockHistoryCache = stockHistoryCache;
        this.fundHistoryCache = fundHistoryCache;
    }

    public StockHistoryResponse getStockHistory(String provider, String code, LocalDate startDate, LocalDate endDate) {
        JsonNode root = fetch(provider, "STOCK", code, startDate, endDate, "day");
        String name = root.path("name").asText(code);
        TreeMap<LocalDate, StockHistoryPoint> pointsByDate = new TreeMap<>();
        for (JsonNode row : root.path("points")) {
            StockHistoryPoint point = parseStockPoint(row, code);
            if (point != null) {
                LocalDate date = LocalDate.parse(point.getDate());
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    pointsByDate.put(date, point);
                }
            }
        }
        ArrayList<StockHistoryPoint> points = new ArrayList<>(pointsByDate.values());
        calculateStockChanges(points);
        StockHistoryResponse response = new StockHistoryResponse();
        response.setCode(code);
        response.setName(name);
        response.setFrequency("day");
        response.setPoints(points);
        stockHistoryCache.save(code, name, points);
        return response;
    }

    public FundHistoryResponse getFundHistory(String provider, String code, LocalDate startDate,
                                              LocalDate endDate, String frequency) {
        JsonNode root = fetch(provider, "FUND", code, startDate, endDate, frequency);
        String name = root.path("name").asText(code);
        TreeMap<LocalDate, FundHistoryPoint> pointsByDate = new TreeMap<>();
        for (JsonNode row : root.path("points")) {
            FundHistoryPoint point = parseFundPoint(row);
            if (point != null) {
                LocalDate date = LocalDate.parse(point.getDate());
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    pointsByDate.put(date, point);
                }
            }
        }
        ArrayList<FundHistoryPoint> points = new ArrayList<>(pointsByDate.values());
        calculateFundChanges(points);
        FundHistoryResponse response = new FundHistoryResponse();
        response.setCode(code);
        response.setName(name);
        response.setFrequency(frequency);
        response.setPoints(points);
        response.setAnalysis(FundHistoryAnalyzer.analyzeOnExchange(points, frequency));
        fundHistoryCache.saveOnExchange(code, name, frequency, points);
        return response;
    }

    private JsonNode fetch(String provider, String instrumentType, String code, LocalDate startDate,
                           LocalDate endDate, String frequency) {
        boolean acquired = false;
        try {
            requestLimiter.acquire();
            acquired = true;
            if (!"EASTMONEY".equals(provider)) {
                throw new BizException("不支持的备用历史行情 API：" + provider);
            }
            HttpRequest request = eastmoneyRequest(instrumentType, code, startDate, endDate, frequency);
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(provider + " 历史行情 API 请求失败，HTTP " + response.statusCode());
            }
            JsonNode responseJson = OBJECT_MAPPER.readTree(response.body());
            return parseEastmoneyResponse(responseJson, code);
        } catch (IOException e) {
            log.warn("{} history request failed: {}", provider, e.getMessage());
            throw new BizException(provider + " 历史行情 API 连接失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("历史行情查询被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("历史行情 API 地址或参数无效");
        } finally {
            if (acquired) {
                requestLimiter.release();
            }
        }
    }

    private HttpRequest eastmoneyRequest(String instrumentType, String code, LocalDate startDate,
                                         LocalDate endDate, String frequency) {
        int market = code.startsWith("6") || code.startsWith("5") ? 1 : 0;
        String secId = market + "." + code;
        String klt = switch (frequency) {
            case "day" -> "101";
            case "week" -> "102";
            case "month" -> "103";
            default -> throw new BizException("行情周期仅支持日、周、月");
        };
        if ("STOCK".equals(instrumentType) && !"day".equals(frequency)) {
            throw new BizException("股票历史行情仅支持日线");
        }
        String params = "secid=" + secId
                + "&fields1=f1,f2,f3,f4,f5,f6"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                + "&klt=" + klt + "&fqt=1&beg=" + startDate.toString().replace("-", "")
                + "&end=" + endDate.toString().replace("-", "")
                + "&lmt=1000000";
        return HttpRequest.newBuilder(URI.create(eastmoneyHistoryUrl + "?" + params))
                .timeout(Duration.ofSeconds(20))
                .header("Referer", "https://quote.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
    }

    private JsonNode parseEastmoneyResponse(JsonNode response, String code) {
        JsonNode data = response == null ? null : response.path("data");
        JsonNode klines = data == null ? null : data.path("klines");
        if (data == null || data.isMissingNode() || data.isNull() || klines == null || !klines.isArray()) {
            throw new BizException("东方财富历史行情 API 未找到代码 " + code + " 或没有返回行情数据");
        }
        var points = OBJECT_MAPPER.createArrayNode();
        for (JsonNode line : klines) {
            String[] fields = line.asText().split(",", -1);
            if (fields.length < 6) {
                continue;
            }
            var point = OBJECT_MAPPER.createObjectNode();
            point.put("date", fields[0]);
            point.put("open", fields[1]);
            point.put("close", fields[2]);
            point.put("high", fields[3]);
            point.put("low", fields[4]);
            point.put("volume", fields[5]);
            points.add(point);
        }
        var result = OBJECT_MAPPER.createObjectNode();
        result.put("name", data.path("name").asText(code));
        result.set("points", points);
        return result;
    }

    private StockHistoryPoint parseStockPoint(JsonNode row, String code) {
        try {
            StockHistoryPoint point = new StockHistoryPoint();
            point.setDate(row.path("date").asText());
            point.setOpen(new BigDecimal(row.path("open").asText()));
            point.setClose(new BigDecimal(row.path("close").asText()));
            point.setHigh(new BigDecimal(row.path("high").asText()));
            point.setLow(new BigDecimal(row.path("low").asText()));
            point.setVolume(new BigDecimal(row.path("volume").asText()));
            LocalDate.parse(point.getDate());
            return point.getClose().signum() > 0 ? point : null;
        } catch (NumberFormatException | DateTimeParseException e) {
            log.warn("Skipping invalid alternative stock history row for {}: {}", code, e.getMessage());
            return null;
        }
    }

    private FundHistoryPoint parseFundPoint(JsonNode row) {
        try {
            FundHistoryPoint point = new FundHistoryPoint();
            point.setDate(row.path("date").asText());
            point.setOpen(new BigDecimal(row.path("open").asText()));
            point.setClose(new BigDecimal(row.path("close").asText()));
            point.setHigh(new BigDecimal(row.path("high").asText()));
            point.setLow(new BigDecimal(row.path("low").asText()));
            point.setVolume(new BigDecimal(row.path("volume").asText()));
            LocalDate.parse(point.getDate());
            return point;
        } catch (NumberFormatException | DateTimeParseException e) {
            log.warn("Skipping invalid alternative fund history row: {}", e.getMessage());
            return null;
        }
    }

    private void calculateStockChanges(List<StockHistoryPoint> points) {
        BigDecimal previousClose = null;
        for (StockHistoryPoint point : points) {
            point.setChangePercent(changePercent(point.getClose(), previousClose));
            previousClose = point.getClose();
        }
    }

    private void calculateFundChanges(List<FundHistoryPoint> points) {
        BigDecimal previousClose = null;
        for (FundHistoryPoint point : points) {
            point.setChangePercent(changePercent(point.getClose(), previousClose));
            previousClose = point.getClose();
        }
    }

    private BigDecimal changePercent(BigDecimal close, BigDecimal previousClose) {
        return previousClose == null || previousClose.signum() == 0
                ? BigDecimal.ZERO
                : close.subtract(previousClose).multiply(BigDecimal.valueOf(100))
                        .divide(previousClose, 2, java.math.RoundingMode.HALF_UP);
    }

}
