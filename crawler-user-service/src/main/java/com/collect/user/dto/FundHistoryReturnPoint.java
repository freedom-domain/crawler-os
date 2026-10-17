package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundHistoryReturnPoint {

    private String period;
    private BigDecimal returnPercent;

    public FundHistoryReturnPoint(String period, BigDecimal returnPercent) {
        this.period = period;
        this.returnPercent = returnPercent;
    }
}
