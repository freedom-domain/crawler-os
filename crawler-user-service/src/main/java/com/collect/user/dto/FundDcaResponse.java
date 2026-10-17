package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FundDcaResponse {

    private String code;
    private String name;
    private String startDate;
    private String endDate;
    private Integer weekday;
    private BigDecimal amountPerPeriod;
    private Integer executedPeriods;
    private Integer skippedPeriods;
    private BigDecimal totalContribution;
    private BigDecimal investedAmount;
    private BigDecimal totalCommission;
    private BigDecimal remainingCash;
    private Integer totalShares;
    private BigDecimal averageCost;
    private BigDecimal finalPrice;
    private BigDecimal marketValue;
    private BigDecimal totalAssets;
    private BigDecimal profit;
    private BigDecimal profitPercent;
    private String finalQuoteDate;
    private List<FundDcaEntry> schedule;
    private FundLumpSumComparison lumpSumComparison;
}
