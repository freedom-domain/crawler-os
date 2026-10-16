package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvestmentTradeResponse {

    private Long id;
    private String code;
    private String name;
    private String market;
    private String instrumentType;
    private String side;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal commission;
    private BigDecimal stampTax;
    private LocalDateTime tradeTime;
}
