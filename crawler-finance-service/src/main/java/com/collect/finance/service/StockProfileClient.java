package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.dto.StockCatalogItem;
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

@Slf4j
@Component
public class StockProfileClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${investment.eastmoney-stock-profile-url:https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/PageAjax}")
    private String profileUrl;

    public void enrich(StockCatalogItem item) {
        JsonNode root = fetch(item.getMarket(), item.getCode());
        JsonNode profile = root.path("jbzl");
        if (profile.isArray()) {
            profile = profile.isEmpty() ? null : profile.get(0);
        }
        if (profile == null || !profile.isObject()) {
            throw new BizException("股票档案服务未返回有效的公司资料");
        }

        item.setCompanyName(text(profile, "ORG_NAME", "ORG_SHORT_NAME"));
        item.setFormerNames(text(profile, "FORMERNAME"));
        item.setLegalRepresentative(text(profile, "LEGAL_PERSON"));
        item.setRegisteredCapital(text(profile, "REG_CAPITAL"));
        item.setEstablishedDate(text(profile, "FOUND_DATE"));
        item.setProvince(text(profile, "PROVINCE"));
        item.setEmployeeCount(text(profile, "EMP_NUM"));
        item.setMainBusiness(text(profile, "MAIN_BUSINESS"));
        item.setBusinessScope(text(profile, "BUSINESS_SCOPE"));
        item.setCompanyProfile(text(profile, "ORG_PROFILE", "ORG_INTRODUCTION"));
        item.setRegisteredAddress(text(profile, "ORG_ADDR", "REG_ADDRESS"));
        item.setWebsite(text(profile, "ORG_WEB", "ORG_WEBSITE"));
        item.setEmail(text(profile, "ORG_EMAIL"));
        item.setPhone(text(profile, "ORG_TEL"));
        if (item.getCompanyName() == null && item.getCompanyProfile() == null
                && item.getMainBusiness() == null) {
            throw new BizException("股票档案服务没有返回可用的公司背景信息");
        }
    }

    private JsonNode fetch(String market, String code) {
        String marketCode = "北京".equals(market) ? "BJ" : "上海".equals(market) ? "SH" : "SZ";
        String url = profileUrl + "?code=" + marketCode + code;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Referer", "https://quote.eastmoney.com/")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("股票档案服务暂不可用，HTTP " + response.statusCode());
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (IOException e) {
            log.warn("Eastmoney stock profile request failed for {}: {}", code, e.getMessage());
            throw new BizException("连接股票档案服务失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("股票档案查询被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("股票档案服务地址无效");
        }
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText().trim();
                if (!text.isBlank() && !text.equals("--")) {
                    return text;
                }
            }
        }
        return null;
    }
}
