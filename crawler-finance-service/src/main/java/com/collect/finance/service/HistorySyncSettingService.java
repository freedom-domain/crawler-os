package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.config.TencentHistoryRequestLimiter;
import com.collect.finance.dto.HistorySyncConcurrencyResponse;
import com.collect.finance.mapper.HistorySyncSettingMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class HistorySyncSettingService {

    private static final int MIN_CONCURRENCY = 1;
    private static final int MAX_CONCURRENCY = 20;

    private final HistorySyncSettingMapper settingMapper;
    private final TencentHistoryRequestLimiter requestLimiter;

    public HistorySyncSettingService(HistorySyncSettingMapper settingMapper,
                                     TencentHistoryRequestLimiter requestLimiter) {
        this.settingMapper = settingMapper;
        this.requestLimiter = requestLimiter;
    }

    @PostConstruct
    public void loadConcurrency() {
        Integer concurrency = settingMapper.selectConcurrency();
        if (concurrency == null || concurrency < MIN_CONCURRENCY || concurrency > MAX_CONCURRENCY) {
            throw new IllegalStateException("历史行情并发配置不存在或超出允许范围 1-20");
        }
        requestLimiter.updateLimit(concurrency);
    }

    public HistorySyncConcurrencyResponse getConcurrency() {
        return new HistorySyncConcurrencyResponse(requestLimiter.getLimit());
    }

    public HistorySyncConcurrencyResponse updateConcurrency(int concurrency) {
        if (concurrency < MIN_CONCURRENCY || concurrency > MAX_CONCURRENCY) {
            throw new BizException("历史行情并发数量必须为 1 到 20 的整数");
        }
        if (settingMapper.updateConcurrency(concurrency) != 1) {
            throw new IllegalStateException("更新历史行情并发配置失败");
        }
        requestLimiter.updateLimit(concurrency);
        return getConcurrency();
    }
}
