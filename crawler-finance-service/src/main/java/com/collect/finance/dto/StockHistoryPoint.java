package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockHistoryPoint {

    private String date;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal volume;
    private BigDecimal changePercent;
}
