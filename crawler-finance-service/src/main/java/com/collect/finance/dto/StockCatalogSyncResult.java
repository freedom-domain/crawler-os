package com.collect.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StockCatalogSyncResult {

    private int total;
    private LocalDateTime syncedAt;
}
