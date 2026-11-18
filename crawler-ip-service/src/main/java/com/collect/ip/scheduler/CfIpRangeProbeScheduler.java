package com.collect.ip.scheduler;

import com.collect.ip.dto.CfIpRangeProbeScheduleReq;
import com.collect.ip.service.CfIpRangeService;
import com.collect.ip.service.CfIpRangeProbeSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@Slf4j
public class CfIpRangeProbeScheduler {

    private final CfIpRangeService rangeService;
    private final CfIpRangeProbeSettingsService settingsService;

    public CfIpRangeProbeScheduler(CfIpRangeService rangeService,
                                   CfIpRangeProbeSettingsService settingsService) {
        this.rangeService = rangeService;
        this.settingsService = settingsService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void probeEnabledRangeSamples() {
        LocalDateTime runAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .withSecond(0).withNano(0);
        probeEnabledRangeSamples(runAt);
    }

    void probeEnabledRangeSamples(LocalDateTime runAt) {
        try {
            CfIpRangeProbeScheduleReq settings = settingsService.getSettings();
            if (!Boolean.TRUE.equals(settings.getEnabled()) || !isDue(settings.getFrequency(), runAt)) {
                return;
            }
            if (!settingsService.claimRun(runAt)) {
                return;
            }
            int tested = rangeService.probeSamples(settings.getSamplesPerRange(), settings.getTlsServerName()).size();
            log.info("Scheduled Cloudflare range probe completed: tested={}, frequency={}",
                    tested, settings.getFrequency());
        } catch (Exception e) {
            log.error("Scheduled Cloudflare range probe failed", e);
        }
    }

    static boolean isDue(String frequency, LocalDateTime time) {
        return switch (frequency) {
            case "EVERY_5_MINUTES" -> time.getMinute() % 5 == 0;
            case "EVERY_15_MINUTES" -> time.getMinute() % 15 == 0;
            case "EVERY_30_MINUTES" -> time.getMinute() % 30 == 0;
            case "HOURLY" -> time.getMinute() == 0;
            case "DAILY" -> time.getMinute() == 0 && time.getHour() == 3;
            case "WEEKLY" -> time.getMinute() == 0 && time.getHour() == 3
                    && time.getDayOfWeek() == DayOfWeek.MONDAY;
            default -> false;
        };
    }
}
