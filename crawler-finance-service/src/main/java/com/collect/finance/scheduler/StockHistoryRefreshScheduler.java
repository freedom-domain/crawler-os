package com.collect.finance.scheduler;

import com.collect.finance.dto.StockWatchlistItem;
import com.collect.finance.service.StockRefreshSettingService;
import com.collect.finance.service.StockWatchlistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class StockHistoryRefreshScheduler {

    private final StockWatchlistService stockWatchlistService;
    private final StockRefreshSettingService settingService;

    public StockHistoryRefreshScheduler(StockWatchlistService stockWatchlistService,
                                        StockRefreshSettingService settingService) {
        this.stockWatchlistService = stockWatchlistService;
        this.settingService = settingService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void refreshWatchedStocks() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        String frequency;
        String slot;
        try {
            frequency = settingService.getSchedule().getFrequency();
            if ("HOURLY".equals(frequency) && now.getMinute() == 0) {
                slot = "H:" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            } else if ("DAILY".equals(frequency)
                    && now.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue()
                    && now.getHour() == 18 && now.getMinute() == 35) {
                slot = "D:" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else if ("WEEKLY".equals(frequency) && now.getDayOfWeek() == DayOfWeek.MONDAY
                    && now.getHour() == 18 && now.getMinute() == 35) {
                slot = "W:" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else {
                return;
            }
            if (!settingService.claimRunSlot(frequency, slot)) {
                return;
            }
        } catch (RuntimeException exception) {
            log.error("Failed to claim scheduled stock history refresh slot", exception);
            return;
        }

        int refreshed = 0;
        int failed = 0;
        for (StockWatchlistItem item : stockWatchlistService.list()) {
            try {
                stockWatchlistService.refresh(item.getCode());
                refreshed++;
            } catch (RuntimeException exception) {
                failed++;
                log.error("Failed to refresh watched stock history: code={}", item.getCode(), exception);
            }
        }
        log.info("Finished scheduled watched stock history refresh: refreshed={}, failed={}", refreshed, failed);
    }
}
