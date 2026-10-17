package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FundCatalogItem {

    private String fundType;
    private String code;
    private String name;
    private String category;
    private String pinyin;
    private Boolean watched;
    private LocalDateTime syncedAt;
    private String watchlistFrequencies;
    private LocalDateTime firstWatchedAt;
    private Long cachedPoints;
    private Long dailyCachedPoints;
    private Long weeklyCachedPoints;
    private Long monthlyCachedPoints;
    private LocalDate cachedStartDate;
    private LocalDate latestMarketDate;
    private String fundFullName;
    private String issueDate;
    private String establishedDate;
    private String establishmentScale;
    private String netAssetScale;
    private String netAssetScaleAsOf;
    private String shareScale;
    private String shareScaleAsOf;
    private String managementCompany;
    private String custodian;
    private String managerNames;
    private String distributionSinceLaunch;
    private String managementFeeRate;
    private String custodianFeeRate;
    private String serviceFeeRate;
    private String maxSubscriptionFeeRate;
    private String maxPurchaseFeeRate;
    private String maxRedemptionFeeRate;
    private String benchmark;
    private String trackingTarget;
    private String investmentObjective;
    private String investmentPhilosophy;
    private String investmentScope;
    private String investmentStrategy;
    private String dividendPolicy;
    private String riskReturnFeature;
    private LocalDateTime profileUpdatedAt;
    private BigDecimal firstMarketValue;
    private BigDecimal periodHigh;
    private BigDecimal periodLow;
    private BigDecimal latestOpen;
    private BigDecimal latestClose;
    private BigDecimal latestHigh;
    private BigDecimal latestLow;
    private BigDecimal latestVolume;
    private BigDecimal latestChangePercent;
    private BigDecimal latestUnitNav;
    private BigDecimal latestCumulativeNav;
}
