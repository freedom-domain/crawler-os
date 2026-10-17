package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.dto.FundCatalogItem;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class EastmoneyFundProfileClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Pattern PROFILE_TABLE = Pattern.compile(
            "(?is)<table\\b[^>]*class=['\"][^'\"]*\\binfo\\b[^'\"]*['\"][^>]*>(.*?)</table>");
    private static final Pattern TABLE_CELL = Pattern.compile("(?is)<(th|td)\\b[^>]*>(.*?)</\\1>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern AS_OF_DATE = Pattern.compile("截止至\\s*[:：]?\\s*([^）)]+)");
    private static final Pattern ESTABLISHMENT = Pattern.compile("^\\s*(.*?)\\s*/\\s*(.*?)\\s*$");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);");

    @Value("${investment.eastmoney-fund-profile-url:https://fundf10.eastmoney.com/jbgk_%s.html}")
    private String profileUrl;

    public void enrich(String code, FundCatalogItem item) {
        String html = fetchProfile(code);
        Matcher tableMatcher = PROFILE_TABLE.matcher(html);
        if (!tableMatcher.find()) {
            throw new BizException("基金档案服务未返回有效的基本资料");
        }

        Map<String, String> fields = parseTable(tableMatcher.group(1));
        item.setFundFullName(fields.get("基金全称"));
        item.setIssueDate(fields.get("发行日期"));
        setEstablishment(fields.get("成立日期/规模"), item);
        setScale(fields.get("净资产规模"), item, true);
        setScale(fields.get("份额规模"), item, false);
        item.setManagementCompany(fields.get("基金管理人"));
        item.setCustodian(fields.get("基金托管人"));
        item.setManagerNames(fields.get("基金经理人"));
        item.setDistributionSinceLaunch(fields.get("成立来分红"));
        item.setManagementFeeRate(fields.get("管理费率"));
        item.setCustodianFeeRate(fields.get("托管费率"));
        item.setServiceFeeRate(fields.get("销售服务费率"));
        item.setMaxSubscriptionFeeRate(fields.get("最高认购费率"));
        item.setMaxPurchaseFeeRate(fields.get("最高申购费率"));
        item.setMaxRedemptionFeeRate(fields.get("最高赎回费率"));
        item.setBenchmark(fields.get("业绩比较基准"));
        item.setTrackingTarget(fields.get("跟踪标的"));
        item.setInvestmentObjective(extractSection(html, "投资目标"));
        item.setInvestmentPhilosophy(extractSection(html, "投资理念"));
        item.setInvestmentScope(extractSection(html, "投资范围"));
        item.setInvestmentStrategy(extractSection(html, "投资策略"));
        item.setDividendPolicy(extractSection(html, "分红政策"));
        item.setRiskReturnFeature(extractSection(html, "风险收益特征"));
    }

    private String fetchProfile(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(profileUrl.formatted(code)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Referer", "https://fund.eastmoney.com/")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "text/html, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("基金档案服务暂不可用，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            log.warn("Eastmoney fund profile request failed for {}: {}", code, e.getMessage());
            throw new BizException("连接基金档案服务失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("基金档案查询被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("基金档案服务地址无效");
        }
    }

    private Map<String, String> parseTable(String html) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = TABLE_CELL.matcher(html);
        String pendingLabel = null;
        while (matcher.find()) {
            String content = cleanText(matcher.group(2));
            if ("th".equalsIgnoreCase(matcher.group(1))) {
                pendingLabel = content;
            } else if (pendingLabel != null) {
                fields.put(pendingLabel, content);
                pendingLabel = null;
            }
        }
        return fields;
    }

    private void setEstablishment(String value, FundCatalogItem item) {
        if (value == null) {
            return;
        }
        Matcher matcher = ESTABLISHMENT.matcher(value);
        if (matcher.matches()) {
            item.setEstablishedDate(matcher.group(1).trim());
            item.setEstablishmentScale(matcher.group(2).trim());
        } else {
            item.setEstablishedDate(value);
        }
    }

    private void setScale(String value, FundCatalogItem item, boolean netAsset) {
        if (value == null) {
            return;
        }
        Matcher matcher = AS_OF_DATE.matcher(value);
        String asOfDate = matcher.find() ? matcher.group(1).trim() : null;
        String scale = value.replaceAll("[（(]\\s*截止至\\s*[:：]?\\s*[^）)]*[）)]", "").trim();
        if (netAsset) {
            item.setNetAssetScale(scale);
            item.setNetAssetScaleAsOf(asOfDate);
        } else {
            item.setShareScale(scale);
            item.setShareScaleAsOf(asOfDate);
        }
    }

    private String extractSection(String html, String sectionName) {
        Pattern section = Pattern.compile(
                "(?is)<label\\b[^>]*class=['\"][^'\"]*\\bleft\\b[^'\"]*['\"][^>]*>\\s*"
                        + Pattern.quote(sectionName)
                        + "\\s*</label>.*?<p\\b[^>]*>(.*?)</p>");
        Matcher matcher = section.matcher(html);
        return matcher.find() ? cleanText(matcher.group(1)) : null;
    }

    private String cleanText(String html) {
        String text = TAG.matcher(html).replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        Matcher entityMatcher = NUMERIC_ENTITY.matcher(text);
        StringBuffer decoded = new StringBuffer();
        while (entityMatcher.find()) {
            int codePoint = entityMatcher.group(1).startsWith("x")
                    ? Integer.parseInt(entityMatcher.group(1).substring(1), 16)
                    : Integer.parseInt(entityMatcher.group(1));
            entityMatcher.appendReplacement(decoded, Matcher.quoteReplacement(
                    new String(Character.toChars(codePoint))));
        }
        entityMatcher.appendTail(decoded);
        return decoded.toString().replaceAll("\\s+", " ").trim();
    }
}
