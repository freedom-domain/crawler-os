package com.collect.finance.dto;

import lombok.Data;

import java.util.List;

@Data
public class OffExchangeFundHistoryResponse {

    private String code;
    private String name;
    private List<OffExchangeFundNavPoint> points;
    private FundHistoryAnalysis analysis;
}
