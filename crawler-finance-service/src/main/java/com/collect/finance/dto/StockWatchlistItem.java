package com.collect.finance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockWatchlistItem {
    private String code;
    private String name;
    private String market;
    private String industry;
    private Long cachedPoints;
    private LocalDate latestDate;
    private LocalDateTime addedAt;
}
