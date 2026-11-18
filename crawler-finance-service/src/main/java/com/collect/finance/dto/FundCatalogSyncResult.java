package com.collect.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FundCatalogSyncResult {

    private int total;
    private int onshore;
    private int offshore;
    private LocalDateTime syncedAt;
}
