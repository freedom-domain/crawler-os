package com.collect.finance.scheduler;

import com.collect.finance.entity.FundHistoryCacheKey;
import com.collect.finance.service.FundManagementService;
import com.collect.finance.service.FundRefreshSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class FundHistoryRefreshScheduler {

    private final FundManagementService fundManagementService;
    private final FundRefreshSettingService settingService;

    public FundHistoryRefreshScheduler(FundManagementService fundManagementService,
                                       FundRefreshSettingService settingService) {
        this.fundManagementService = fundManagementService;
        this.settingService = settingService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void refreshCachedFundHistory() {
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        String frequency;
        String slot;
        try {
            frequency = settingService.getSchedule().getFrequency();
            if ("HOURLY".equals(frequency) && now.getMinute() == 0) {
                slot = "H:" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            } else if ("DAILY".equals(frequency)
                    && now.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue()
                    && now.getHour() == 18 && now.getMinute() == 30) {
                slot = "D:" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else if ("WEEKLY".equals(frequency) && now.getDayOfWeek() == DayOfWeek.MONDAY
                    && now.getHour() == 18 && now.getMinute() == 30) {
                slot = "W:" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else {
                return;
            }
            if (!settingService.claimRunSlot(frequency, slot)) {
                return;
            }
        } catch (RuntimeException exception) {
            log.error("Failed to claim scheduled fund history refresh slot", exception);
            return;
        }

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
