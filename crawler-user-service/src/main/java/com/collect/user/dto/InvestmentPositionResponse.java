package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentPositionResponse {

    private String code;
    private String name;
    private String market;
    private String instrumentType;
    private Integer quantity;
    private Integer availableQuantity;
    private BigDecimal averageCost;
    private BigDecimal currentPrice;
    private BigDecimal costValue;
    private BigDecimal marketValue;
    private BigDecimal unrealizedProfit;
    private BigDecimal profitPercent;
    private String quoteTime;
}
