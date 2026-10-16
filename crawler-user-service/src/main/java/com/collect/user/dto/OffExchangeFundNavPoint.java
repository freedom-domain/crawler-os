package com.collect.user.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OffExchangeFundNavPoint {

    private String date;
    private BigDecimal unitNav;
    private BigDecimal cumulativeNav;
    private BigDecimal changePercent;
}
