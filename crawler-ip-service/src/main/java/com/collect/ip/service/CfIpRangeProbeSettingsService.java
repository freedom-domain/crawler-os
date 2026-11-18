package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.dto.CfIpRangeProbeScheduleReq;
import com.collect.ip.entity.CfIpRangeProbeSchedule;
import com.collect.ip.mapper.CfIpRangeProbeScheduleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CfIpRangeProbeSettingsService {

    private final CfIpRangeProbeScheduleMapper mapper;

    public CfIpRangeProbeSettingsService(CfIpRangeProbeScheduleMapper mapper) {
        this.mapper = mapper;
    }

    public CfIpRangeProbeScheduleReq getSettings() {
        CfIpRangeProbeSchedule settings = mapper.selectSettings();
        if (settings == null) {
            throw new BizException("定时抽样配置不存在，请执行数据库迁移");
        }
        return toRequest(settings);
    }

    @Transactional
    public CfIpRangeProbeScheduleReq updateSettings(CfIpRangeProbeScheduleReq request) {
        request.setFrequency(request.getFrequency().trim().toUpperCase(Locale.ROOT));
        request.setTlsServerName(request.getTlsServerName().trim().toLowerCase(Locale.ROOT));
        mapper.updateSettings(request);
        return getSettings();
    }

    public boolean claimRun(java.time.LocalDateTime runAt) {
        return mapper.claimRun(runAt) == 1;
    }

    private CfIpRangeProbeScheduleReq toRequest(CfIpRangeProbeSchedule settings) {
        CfIpRangeProbeScheduleReq request = new CfIpRangeProbeScheduleReq();
        request.setEnabled(settings.getEnabled());
        request.setFrequency(settings.getFrequency());
        request.setSamplesPerRange(settings.getSamplesPerRange());
        request.setTlsServerName(settings.getTlsServerName());
        return request;
    }
}
