package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundHistoryPoint {

    private String date;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal volume;
    private BigDecimal changePercent;
}
