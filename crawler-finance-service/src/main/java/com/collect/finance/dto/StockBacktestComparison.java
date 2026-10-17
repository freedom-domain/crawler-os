package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockBacktestComparison {

    private String investmentDate;
    private BigDecimal totalContribution;
    private BigDecimal investedAmount;
    private BigDecimal totalFees;
    private BigDecimal finalValue;
    private BigDecimal profit;
    private BigDecimal profitPercent;
}
