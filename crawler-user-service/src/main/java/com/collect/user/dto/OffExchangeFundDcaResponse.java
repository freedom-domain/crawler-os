package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OffExchangeFundDcaResponse {

    private String code;
    private String name;
    private String startDate;
    private String endDate;
    private Integer weekday;
    private BigDecimal amountPerPeriod;
    private BigDecimal subscriptionFeeRate;
    private BigDecimal redemptionFeeRate;
    private Integer executedPeriods;
    private Integer skippedPeriods;
    private BigDecimal totalContribution;
    private BigDecimal totalSubscriptionFee;
    private BigDecimal totalShares;
    private BigDecimal finalNav;
    private BigDecimal grossMarketValue;
    private BigDecimal redemptionFee;
    private BigDecimal netLiquidationValue;
    private BigDecimal profit;
    private BigDecimal profitPercent;
    private String finalNavDate;
    private List<OffExchangeFundDcaEntry> schedule;
    private FundLumpSumComparison lumpSumComparison;
}
