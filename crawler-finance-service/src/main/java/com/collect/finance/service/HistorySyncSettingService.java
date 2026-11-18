package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.config.HistoryRequestLimiter;
import com.collect.finance.dto.HistorySyncConcurrencyResponse;
import com.collect.finance.mapper.HistorySyncSettingMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class HistorySyncSettingService {

    private static final int MIN_CONCURRENCY = 1;
    private static final int MAX_CONCURRENCY = 20;
    private static final int MIN_REQUEST_INTERVAL_MILLIS = 0;
    private static final int MAX_REQUEST_INTERVAL_MILLIS = 60_000;
    private static final Set<String> SUPPORTED_PROVIDERS =
            Set.of("TENCENT", "EASTMONEY");

    private final HistorySyncSettingMapper settingMapper;
    private final HistoryRequestLimiter requestLimiter;
    private final ThreadPoolTaskExecutor historySyncExecutor;

    public HistorySyncSettingService(HistorySyncSettingMapper settingMapper,
                                     HistoryRequestLimiter requestLimiter,
                                     @Qualifier("historySyncExecutor") ThreadPoolTaskExecutor historySyncExecutor) {
        this.settingMapper = settingMapper;
        this.requestLimiter = requestLimiter;
        this.historySyncExecutor = historySyncExecutor;
    }

    @PostConstruct
    public void loadConcurrency() {
        Integer concurrency = settingMapper.selectConcurrency();
        if (concurrency == null || concurrency < MIN_CONCURRENCY || concurrency > MAX_CONCURRENCY) {
            throw new IllegalStateException("历史行情并发配置不存在或超出允许范围 1-20");
        }
        Integer requestIntervalMillis = settingMapper.selectRequestIntervalMillis();
        if (requestIntervalMillis == null || requestIntervalMillis < MIN_REQUEST_INTERVAL_MILLIS
                || requestIntervalMillis > MAX_REQUEST_INTERVAL_MILLIS) {
            throw new IllegalStateException("历史行情请求间隔配置不存在或超出允许范围 0-60000 毫秒");
        }
        applyConcurrency(concurrency);
        requestLimiter.updateIntervalMillis(requestIntervalMillis);
        parseProviders(settingMapper.selectEnabledApis());
    }

    public HistorySyncConcurrencyResponse getConcurrency() {
        return new HistorySyncConcurrencyResponse(requestLimiter.getLimit(),
                parseProviders(settingMapper.selectEnabledApis()), requestLimiter.getIntervalMillis());
    }

    public HistorySyncConcurrencyResponse updateSettings(int concurrency, List<String> rawProviders,
                                                          int requestIntervalMillis) {
        if (concurrency < MIN_CONCURRENCY || concurrency > MAX_CONCURRENCY) {
            throw new BizException("历史行情并发数量必须为 1 到 20 的整数");
        }
        if (requestIntervalMillis < MIN_REQUEST_INTERVAL_MILLIS
                || requestIntervalMillis > MAX_REQUEST_INTERVAL_MILLIS) {
            throw new BizException("历史行情请求间隔必须为 0 到 60000 毫秒");
        }
        List<String> providers = normalizeProviders(rawProviders);
        if (settingMapper.updateSettings(concurrency, String.join(",", providers), requestIntervalMillis) != 1) {
            throw new IllegalStateException("更新历史行情并发配置失败");
        }
        applyConcurrency(concurrency);
        requestLimiter.updateIntervalMillis(requestIntervalMillis);
        return getConcurrency();
    }

    private void applyConcurrency(int concurrency) {
        int currentMaximum = historySyncExecutor.getMaxPoolSize();
        if (concurrency > currentMaximum) {
            historySyncExecutor.setMaxPoolSize(concurrency);
            historySyncExecutor.setCorePoolSize(concurrency);
        } else {
            historySyncExecutor.setCorePoolSize(concurrency);
            historySyncExecutor.setMaxPoolSize(concurrency);
        }
        requestLimiter.updateLimit(concurrency);
    }

    public void requireEnabledProvider(String rawProvider) {
        String provider = normalizeProvider(rawProvider);
        if (!parseProviders(settingMapper.selectEnabledApis()).contains(provider)) {
            throw new BizException("历史行情 API 未在同步并发设置中启用：" + provider);
        }
    }

    private List<String> parseProviders(String rawProviders) {
        if (rawProviders == null || rawProviders.isBlank()) {
            throw new IllegalStateException("历史行情 API 配置为空");
        }
        return normalizeProviders(Arrays.asList(rawProviders.split(",")));
    }

    private List<String> normalizeProviders(List<String> rawProviders) {
        if (rawProviders == null || rawProviders.isEmpty()) {
            throw new BizException("至少启用一个历史行情 API");
        }
        List<String> providers = rawProviders.stream()
                .map(this::normalizeProvider)
                .distinct()
                .toList();
        if (providers.stream().anyMatch(provider -> !SUPPORTED_PROVIDERS.contains(provider))) {
            throw new BizException("历史行情 API 仅支持 TENCENT、EASTMONEY");
        }
        return providers;
    }

    private String normalizeProvider(String rawProvider) {
        return rawProvider == null ? "" : rawProvider.trim().toUpperCase(Locale.ROOT);
    }
}
