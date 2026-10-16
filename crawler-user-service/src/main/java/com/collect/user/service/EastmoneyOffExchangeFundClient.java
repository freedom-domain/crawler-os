package com.collect.user.service;

import com.collect.common.exception.BizException;
import com.collect.user.dto.OffExchangeFundHistoryResponse;
import com.collect.user.dto.OffExchangeFundNavPoint;
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
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class EastmoneyOffExchangeFundClient {

    private static final Pattern FUND_CODE = Pattern.compile("\\d{6}");
    private static final Pattern FUND_NAME = Pattern.compile("fS_name\\s*=\\s*\"([^\"]+)\"");
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 150;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${investment.eastmoney-nav-url:https://api.fund.eastmoney.com/f10/lsjz}")
    private String historyUrl;

    @Value("${investment.eastmoney-fund-info-url:https://fund.eastmoney.com/pingzhongdata/%s.js}")
    private String fundInfoUrl;

    public OffExchangeFundHistoryResponse getHistory(String code, LocalDate startDate, LocalDate endDate) {
        validateCode(code);
        TreeMap<LocalDate, OffExchangeFundNavPoint> pointMap = new TreeMap<>();
        int totalCount = Integer.MAX_VALUE;
        int pageSize = PAGE_SIZE;
        for (int page = 1; page <= MAX_PAGES && (page - 1) * pageSize < totalCount; page++) {
            JsonNode root = fetchHistoryPage(code, startDate, endDate, page);
            if (root.path("ErrCode").asInt(-1) != 0) {
                throw new BizException("基金净值服务返回错误：" + root.path("ErrMsg").asText("未知错误"));
            }
            JsonNode data = root.path("Data");
            if (!data.isObject()) {
                throw new BizException("基金净值服务未返回有效数据");
            }
            JsonNode rows = data.path("LSJZList");
            totalCount = root.path("TotalCount").asInt(0);
            pageSize = Math.max(1, root.path("PageSize").asInt(PAGE_SIZE));
            if (!rows.isArray()) {
                throw new BizException("基金净值服务返回了无法识别的数据");
            }
            for (JsonNode row : rows) {
                OffExchangeFundNavPoint point = parsePoint(row, code);
                if (point != null) {
                    pointMap.put(LocalDate.parse(point.getDate()), point);
                }
            }
            if (rows.isEmpty()) {
                break;
            }
        }
        if (pointMap.size() < totalCount) {
            throw new BizException("基金净值数据量超出单次查询上限，请缩短日期范围");
        }
        if (pointMap.isEmpty()) {
            throw new BizException("该基金在所选时间段没有可用的历史净值");
        }

        OffExchangeFundHistoryResponse result = new OffExchangeFundHistoryResponse();
        result.setCode(code);
        result.setName(fetchFundName(code));
        result.setPoints(new ArrayList<>(pointMap.values()));
        return result;
    }

    private OffExchangeFundNavPoint parsePoint(JsonNode row, String code) {
        String date = row.path("FSRQ").asText();
        String nav = row.path("DWJZ").asText();
        if (date.isBlank() || nav.isBlank() || nav.equals("---")) {
            return null;
        }
        try {
            OffExchangeFundNavPoint point = new OffExchangeFundNavPoint();
            point.setDate(date);
            point.setUnitNav(new BigDecimal(nav));
            String cumulativeNav = row.path("LJJZ").asText();
            point.setCumulativeNav(cumulativeNav.isBlank() || cumulativeNav.equals("---")
                    ? null : new BigDecimal(cumulativeNav));
            String changePercent = row.path("JZZZL").asText();
            point.setChangePercent(changePercent.isBlank() || changePercent.equals("---")
                    ? BigDecimal.ZERO : new BigDecimal(changePercent));
            return point;
        } catch (NumberFormatException e) {
            log.warn("Skipping invalid Eastmoney NAV row for {} on {}: {}", code, date, e.getMessage());
            return null;
        }
    }

    private JsonNode fetchHistoryPage(String code, LocalDate startDate, LocalDate endDate, int page) {
        String query = "?fundCode=" + code
                + "&pageIndex=" + page
                + "&pageSize=" + PAGE_SIZE
                + "&startDate=" + startDate
                + "&endDate=" + endDate;
        return getJson(historyUrl + query, "https://fundf10.eastmoney.com/");
    }

    private String fetchFundName(String code) {
        String url = fundInfoUrl.formatted(code);
        String body = getText(url, "https://fund.eastmoney.com/");
        Matcher matcher = FUND_NAME.matcher(body);
        if (!matcher.find()) {
            throw new BizException("无法获取场外基金 " + code + " 的名称，请确认基金代码有效");
        }
        return matcher.group(1);
    }

    private JsonNode getJson(String url, String referer) {
        try {
            return OBJECT_MAPPER.readTree(getText(url, referer));
        } catch (IOException e) {
            log.warn("Eastmoney NAV response parsing failed: {}", e.getMessage());
            throw new BizException("基金净值服务返回了无效数据");
        }
    }

    private String getText(String url, String referer) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Referer", referer)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json, text/javascript, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("基金净值服务暂不可用，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            log.warn("Eastmoney NAV request failed: {}", e.getMessage());
            throw new BizException("连接基金净值服务失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("基金净值查询被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("基金净值服务地址无效");
        }
    }

    private void validateCode(String code) {
        if (code == null || !FUND_CODE.matcher(code).matches()) {
            throw new BizException("场外基金代码应为 6 位数字");
        }
    }
}
