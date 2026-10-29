package com.collect.ip.scheduler;

import com.collect.ip.dto.CfIpRangeProbeScheduleReq;
import com.collect.ip.service.CfIpRangeProbeSettingsService;
import com.collect.ip.service.CfIpRangeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CfIpRangeProbeSchedulerTest {

    private final CfIpRangeService rangeService = mock(CfIpRangeService.class);
    private final CfIpRangeProbeSettingsService settingsService = mock(CfIpRangeProbeSettingsService.class);
    private final CfIpRangeProbeScheduler scheduler = new CfIpRangeProbeScheduler(rangeService, settingsService);

    @Test
    void runsHourlyAtTheTopOfTheHour() {
        assertTrue(CfIpRangeProbeScheduler.isDue("HOURLY", LocalDateTime.of(2026, 10, 29, 11, 0)));
        assertFalse(CfIpRangeProbeScheduler.isDue("HOURLY", LocalDateTime.of(2026, 10, 29, 11, 1)));
    }

    @Test
    void runsDailyAndWeeklyAtThreeInTheMorning() {
        assertTrue(CfIpRangeProbeScheduler.isDue("DAILY", LocalDateTime.of(2026, 10, 29, 3, 0)));
        assertFalse(CfIpRangeProbeScheduler.isDue("DAILY", LocalDateTime.of(2026, 10, 29, 4, 0)));
        assertTrue(CfIpRangeProbeScheduler.isDue("WEEKLY", LocalDateTime.of(2026, 10, 26, 3, 0)));
        assertFalse(CfIpRangeProbeScheduler.isDue("WEEKLY", LocalDateTime.of(2026, 10, 27, 3, 0)));
    }

    @Test
    void skipsProbeWhenScheduleIsDisabled() {
        CfIpRangeProbeScheduleReq settings = new CfIpRangeProbeScheduleReq();
        settings.setEnabled(false);
        settings.setFrequency("HOURLY");
        when(settingsService.getSettings()).thenReturn(settings);

        scheduler.probeEnabledRangeSamples(LocalDateTime.of(2026, 10, 29, 11, 0));

        verify(rangeService, never()).probeSamples(anyInt(), anyString());
        verify(settingsService, never()).claimRun(any());
    }

    @Test
    void claimsDueRunBeforeProbingWithSavedSettings() {
        CfIpRangeProbeScheduleReq settings = new CfIpRangeProbeScheduleReq();
        settings.setEnabled(true);
        settings.setFrequency("HOURLY");
        settings.setSamplesPerRange(2);
        settings.setTlsServerName("example.com");
        when(settingsService.getSettings()).thenReturn(settings);
        when(settingsService.claimRun(any())).thenReturn(true);
        when(rangeService.probeSamples(2, "example.com")).thenReturn(List.of());

        scheduler.probeEnabledRangeSamples(LocalDateTime.of(2026, 10, 29, 11, 0));

        verify(settingsService).claimRun(any());
        verify(rangeService).probeSamples(2, "example.com");
    }
}
