package com.collect.ip.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CfIpCandidateSummary {

    private Long total;
    private Long availableCount;
    private Long availableAddressCount;
    private Long pendingCount;
    private Long failedCount;
    private BigDecimal fastestTlsMs;
}
