package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentQuote {

    private String code;
    private String name;
    private String market;
    private String instrumentType;
    private BigDecimal currentPrice;
    private BigDecimal previousClose;
    private BigDecimal changePercent;
    private String quoteTime;
}
