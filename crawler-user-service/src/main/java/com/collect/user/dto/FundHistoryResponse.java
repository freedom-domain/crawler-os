package com.collect.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class FundHistoryResponse {

    private String code;
    private String name;
    private String frequency;
    private List<FundHistoryPoint> points;
    private FundHistoryAnalysis analysis;
}
