package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundDcaEntry {

    private String scheduledDate;
    private String tradeDate;
    private BigDecimal price;
    private BigDecimal contribution;
    private Integer shares;
    private BigDecimal investedAmount;
    private BigDecimal commission;
    private Integer cumulativeShares;
    private BigDecimal cumulativeContribution;
    private BigDecimal marketValue;
    private BigDecimal profit;
}
