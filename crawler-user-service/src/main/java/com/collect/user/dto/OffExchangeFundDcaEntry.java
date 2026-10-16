package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OffExchangeFundDcaEntry {

    private String scheduledDate;
    private String tradeDate;
    private BigDecimal nav;
    private BigDecimal contribution;
    private BigDecimal subscriptionFee;
    private BigDecimal shares;
    private BigDecimal cumulativeShares;
    private BigDecimal marketValue;
    private BigDecimal profit;
}
