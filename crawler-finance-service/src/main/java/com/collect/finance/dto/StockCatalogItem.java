package com.collect.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockCatalogItem {

    private String code;
    private String name;
    private String market;
    private String industry;
    private LocalDateTime syncedAt;
    private String companyName;
    private String formerNames;
    private String legalRepresentative;
    private String registeredCapital;
    private String establishedDate;
    private String province;
    private String employeeCount;
    private String mainBusiness;
    private String businessScope;
    private String companyProfile;
    private String registeredAddress;
    private String website;
    private String email;
    private String phone;
    private LocalDateTime profileUpdatedAt;
    private BigDecimal currentPrice;
    private BigDecimal previousClose;
    private BigDecimal changePercent;
    private String quoteTime;
}
