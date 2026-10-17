package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundLumpSumComparison {

    private String investmentDate;
    private BigDecimal totalContribution;
    private BigDecimal investedAmount;
    private BigDecimal totalFees;
    private BigDecimal subscriptionFee;
    private BigDecimal redemptionFee;
    private BigDecimal finalValue;
    private BigDecimal profit;
    private BigDecimal profitPercent;
}
