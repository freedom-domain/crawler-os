package com.collect.finance.dto;

import lombok.Data;

@Data
public class FundRefreshSchedule {
    private String frequency;
    private String triggerTime;
}
