package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class InvestmentAccountResponse {

    private BigDecimal initialCash;
    private BigDecimal cashBalance;
    private BigDecimal marketValue;
    private BigDecimal totalAssets;
    private BigDecimal totalProfit;
    private BigDecimal profitPercent;
    private List<InvestmentPositionResponse> positions;
    private List<InvestmentTradeResponse> recentTrades;
}
