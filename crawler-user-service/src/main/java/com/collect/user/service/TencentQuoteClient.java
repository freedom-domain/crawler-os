package com.collect.user.service;

import com.collect.common.exception.BizException;
import com.collect.user.dto.InvestmentQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TencentQuoteClient {

    private static final Charset GBK = Charset.forName("GBK");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("v_(sh|sz|bj)(\\d{6})=\"([^\"]*)\"");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${investment.tencent-quote-url:https://qt.gtimg.cn/q=}")
    private String quoteUrl;

    public InvestmentQuote getQuote(String code) {
        InvestmentQuote quote = getQuotes(List.of(code)).get(code);
        if (quote == null) {
            throw new BizException("行情服务未返回证券代码 " + code
                    + " 的报价，请确认代码正确且该证券已在沪深北市场上市");
        }
        return quote;
    }

    public Map<String, InvestmentQuote> getQuotes(Collection<String> codes) {
        Map<String, InvestmentQuote> result = new LinkedHashMap<>();
        List<String> codeList = codes.stream().distinct().toList();
        for (int offset = 0; offset < codeList.size(); offset += 50) {
            List<String> batch = codeList.subList(offset, Math.min(offset + 50, codeList.size()));
            String query = batch.stream().map(this::marketCode).reduce((a, b) -> a + "," + b).orElse("");
            String body = fetch(query);
            Matcher matcher = QUOTE_PATTERN.matcher(body);
            while (matcher.find()) {
                InvestmentQuote quote = parseQuote(matcher.group(1), matcher.group(2), matcher.group(3));
                if (quote != null) {
                    result.put(quote.getCode(), quote);
                }
            }
        }
        return result;
    }

    private String fetch(String query) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(quoteUrl + query))
                    .timeout(Duration.ofSeconds(8))
                    .header("Referer", "https://gu.qq.com/")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("行情服务暂不可用，HTTP " + response.statusCode());
            }
            return new String(response.body(), GBK);
        } catch (IOException e) {
            log.warn("Tencent quote request failed: {}", e.getMessage());
            throw new BizException("行情服务连接失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("行情查询被中断，请稍后重试");
        } catch (IllegalArgumentException e) {
            throw new BizException("行情服务地址或证券代码无效");
        }
    }

    private InvestmentQuote parseQuote(String marketCode, String code, String raw) {
        String[] fields = raw.split("~", -1);
        if (fields.length < 33 || fields[1].isBlank()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(fields[3]);
            if (price.signum() <= 0) {
                return null;
            }
            InvestmentQuote quote = new InvestmentQuote();
            quote.setCode(code);
            quote.setName(fields[1]);
            quote.setMarket(switch (marketCode) {
                case "sh" -> "上海";
                case "sz" -> "深圳";
                case "bj" -> "北京";
                default -> throw new BizException("不支持的证券市场");
            });
            quote.setInstrumentType(isEtf(code) ? "ETF" : "股票");
            quote.setCurrentPrice(price);
            quote.setPreviousClose(new BigDecimal(fields[4]));
            quote.setChangePercent(new BigDecimal(fields[32]));
            quote.setQuoteTime(formatQuoteTime(fields[30]));
            return quote;
        } catch (NumberFormatException e) {
            log.warn("Invalid quote payload for {}: {}", code, e.getMessage());
            return null;
        }
    }

    private String marketCode(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new BizException("证券代码必须为 6 位数字");
        }
        char first = code.charAt(0);
        if (first == '6' || first == '5') {
            return "sh" + code;
        }
        if (first == '0' || first == '1' || first == '2' || first == '3') {
            return "sz" + code;
        }
        if (first == '4' || first == '8' || first == '9') {
            return "bj" + code;
        }
        throw new BizException("暂不支持该证券市场的代码");
    }

    private boolean isEtf(String code) {
        return code.startsWith("5") || code.startsWith("15") || code.startsWith("16") || code.startsWith("18");
    }

    private String formatQuoteTime(String quoteTime) {
        if (quoteTime != null && quoteTime.matches("\\d{14}")) {
            return quoteTime.substring(0, 4) + "-" + quoteTime.substring(4, 6) + "-" + quoteTime.substring(6, 8)
                    + " " + quoteTime.substring(8, 10) + ":" + quoteTime.substring(10, 12) + ":"
                    + quoteTime.substring(12, 14);
        }
        return quoteTime;
    }
}
