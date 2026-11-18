package com.collect.finance.entity;

import lombok.Data;

@Data
public class FundHistoryCacheKey {

    private String fundType;
    private String code;
    private String frequency;
}
