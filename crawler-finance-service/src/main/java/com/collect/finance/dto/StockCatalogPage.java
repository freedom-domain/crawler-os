package com.collect.finance.dto;

import lombok.Data;

import java.util.List;

@Data
public class StockCatalogPage {

    private List<StockCatalogItem> items;
    private long total;
}
