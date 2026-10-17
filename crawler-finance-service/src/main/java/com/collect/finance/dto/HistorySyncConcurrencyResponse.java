package com.collect.finance.dto;

import java.util.List;

public class HistorySyncConcurrencyResponse {

    private final int concurrency;
    private final List<String> providers;
    private final int requestIntervalMillis;

    public HistorySyncConcurrencyResponse(int concurrency, List<String> providers, int requestIntervalMillis) {
        this.concurrency = concurrency;
        this.providers = List.copyOf(providers);
        this.requestIntervalMillis = requestIntervalMillis;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public List<String> getProviders() {
        return providers;
    }

    public int getRequestIntervalMillis() {
        return requestIntervalMillis;
    }
}
