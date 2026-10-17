package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockBacktestEntry {

    private String scheduledDate;
    private String tradeDate;
    private BigDecimal price;
    private BigDecimal contribution;
    private Long shares;
    private BigDecimal investedAmount;
    private BigDecimal commission;
    private Long cumulativeShares;
    private BigDecimal cumulativeContribution;
    private BigDecimal marketValue;
    private BigDecimal profit;
}
