package com.collect.finance.scheduler;

import com.collect.finance.entity.FundHistoryCacheKey;
import com.collect.finance.service.FundManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FundHistoryRefreshScheduler {

    private final FundManagementService fundManagementService;

    public FundHistoryRefreshScheduler(FundManagementService fundManagementService) {
        this.fundManagementService = fundManagementService;
    }

    @Scheduled(cron = "${investment.history-refresh-cron:0 30 18 * * MON-FRI}", zone = "Asia/Shanghai")
    public void refreshCachedFundHistory() {
        int refreshed = 0;
        int failed = 0;
        for (FundHistoryCacheKey key : fundManagementService.getRefreshKeys()) {
            try {
                fundManagementService.refresh(key.getFundType(), key.getCode(), key.getFrequency());
                refreshed++;
            } catch (RuntimeException exception) {
                failed++;
                log.error("Failed to refresh cached fund history: type={}, code={}, frequency={}",
                        key.getFundType(), key.getCode(), key.getFrequency(), exception);
            }
        }
        log.info("Finished scheduled fund history refresh: refreshed={}, failed={}", refreshed, failed);
    }

}
