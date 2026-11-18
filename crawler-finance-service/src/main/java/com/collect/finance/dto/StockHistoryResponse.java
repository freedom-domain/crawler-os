package com.collect.finance.dto;

import lombok.Data;

import java.util.List;

@Data
public class StockHistoryResponse {

    private String code;
    private String name;
    private String frequency;
    private List<StockHistoryPoint> points;
}
