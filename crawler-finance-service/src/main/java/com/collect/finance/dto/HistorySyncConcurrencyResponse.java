package com.collect.finance.dto;

public class HistorySyncConcurrencyResponse {

    private final int concurrency;

    public HistorySyncConcurrencyResponse(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getConcurrency() {
        return concurrency;
    }
}
