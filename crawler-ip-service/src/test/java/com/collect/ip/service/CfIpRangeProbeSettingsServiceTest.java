package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.dto.CfIpRangeProbeScheduleReq;
import com.collect.ip.entity.CfIpRangeProbeSchedule;
import com.collect.ip.mapper.CfIpRangeProbeScheduleMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CfIpRangeProbeSettingsServiceTest {

    private final CfIpRangeProbeScheduleMapper mapper = mock(CfIpRangeProbeScheduleMapper.class);
    private final CfIpRangeProbeSettingsService service = new CfIpRangeProbeSettingsService(mapper);

    @Test
    void normalizesFrequencyAndServerNameWhenSavingSettings() {
        CfIpRangeProbeSchedule current = new CfIpRangeProbeSchedule();
        current.setEnabled(true);
        current.setFrequency("HOURLY");
        current.setSamplesPerRange(2);
        current.setTlsServerName("example.com");
        when(mapper.selectSettings()).thenReturn(current);

        CfIpRangeProbeScheduleReq request = new CfIpRangeProbeScheduleReq();
        request.setEnabled(true);
        request.setFrequency("daily");
        request.setSamplesPerRange(2);
        request.setTlsServerName(" Example.COM ");

        CfIpRangeProbeScheduleReq saved = service.updateSettings(request);

        verify(mapper).updateSettings(argThat(settings -> "DAILY".equals(settings.getFrequency())
                && "example.com".equals(settings.getTlsServerName())));
        assertEquals("HOURLY", saved.getFrequency());
        assertEquals("example.com", saved.getTlsServerName());
    }

    @Test
    void reportsMissingScheduleRow() {
        when(mapper.selectSettings()).thenReturn(null);

        assertThrows(BizException.class, service::getSettings);
    }

    @Test
    void claimRunSqlContainsLessThanOperator() {
        Configuration configuration = new Configuration();
        configuration.addMapper(CfIpRangeProbeScheduleMapper.class);

        String sql = configuration.getMappedStatement(
                        CfIpRangeProbeScheduleMapper.class.getName() + ".claimRun")
                .getBoundSql(Map.of("runAt", LocalDateTime.now()))
                .getSql();

        assertTrue(sql.contains("last_run_at < ?"));
        assertFalse(sql.contains("&lt;"));
    }
}
