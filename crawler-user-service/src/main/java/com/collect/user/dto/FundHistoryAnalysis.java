package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FundHistoryAnalysis {

    private int observationCount;
    private long periodDays;
    private BigDecimal totalReturnPercent;
    private BigDecimal annualizedReturnPercent;
    private BigDecimal maxDrawdownPercent;
    private BigDecimal annualizedVolatilityPercent;
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;
    private String returnSeriesFrequency;
    private List<FundHistoryReturnPoint> monthlyReturns;
    private List<FundHistoryReturnPoint> rollingOneYearReturns;
}
