package com.collect.finance.dto;

import lombok.Data;

import java.util.List;

@Data
public class FundCatalogPage {

    private List<FundCatalogItem> items;
    private long total;
}
