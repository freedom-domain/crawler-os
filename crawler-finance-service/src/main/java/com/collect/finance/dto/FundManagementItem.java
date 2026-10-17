package com.collect.finance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FundManagementItem {

    private Long watchlistId;
    private String fundType;
    private String code;
    private String frequency;
    private String name;
    private Long cachedPoints;
    private LocalDate latestDate;
    private Boolean watched;
    private LocalDateTime addedAt;
}
