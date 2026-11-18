package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.dto.FundRefreshSchedule;
import com.collect.finance.entity.FundRefreshSetting;
import com.collect.finance.mapper.FundRefreshSettingMapper;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Service
public class FundRefreshSettingService {

    private static final int SETTING_ID = 1;
    private static final String DEFAULT_TRIGGER_TIME = "18:30";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> SUPPORTED_FREQUENCIES = Set.of("HOURLY", "DAILY", "WEEKLY");

    private final FundRefreshSettingMapper settingMapper;

    public FundRefreshSettingService(FundRefreshSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public FundRefreshSchedule getSchedule() {
        FundRefreshSetting setting = requireSetting();
        FundRefreshSchedule schedule = new FundRefreshSchedule();
        schedule.setFrequency(setting.getFrequency());
        schedule.setTriggerTime(setting.getTriggerTime() == null ? DEFAULT_TRIGGER_TIME : setting.getTriggerTime());
        return schedule;
    }

    public FundRefreshSchedule updateSchedule(FundRefreshSchedule request) {
        if (request == null || request.getFrequency() == null
                || !SUPPORTED_FREQUENCIES.contains(request.getFrequency())) {
            throw new BizException("同步频率仅支持 HOURLY、DAILY 或 WEEKLY");
        }
        FundRefreshSetting setting = requireSetting();
        String triggerTime = request.getTriggerTime() == null
                ? (setting.getTriggerTime() == null ? DEFAULT_TRIGGER_TIME : setting.getTriggerTime())
                : request.getTriggerTime();
        if (!isValidTriggerTime(triggerTime)) {
            throw new BizException("同步时间格式无效，请使用 HH:mm 格式");
        }
        setting.setFrequency(request.getFrequency());
        setting.setTriggerTime(triggerTime);
        settingMapper.updateById(setting);
        return getSchedule();
    }

    public boolean claimRunSlot(String frequency, String slot) {
        return settingMapper.claimRunSlot(frequency, slot) == 1;
    }

    private FundRefreshSetting requireSetting() {
        FundRefreshSetting setting = settingMapper.selectById(SETTING_ID);
        if (setting == null) {
            throw new BizException("基金同步配置缺失，请先执行数据库迁移 027_fund_refresh_schedule.sql");
        }
        return setting;
    }

    private boolean isValidTriggerTime(String triggerTime) {
        if (triggerTime == null || !triggerTime.matches("\\d{2}:\\d{2}")) {
            return false;
        }
        try {
            LocalTime.parse(triggerTime, TIME_FORMATTER);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
