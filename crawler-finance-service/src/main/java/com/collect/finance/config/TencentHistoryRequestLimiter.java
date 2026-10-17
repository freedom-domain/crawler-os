package com.collect.finance.config;

import org.springframework.stereotype.Component;

@Component
public class TencentHistoryRequestLimiter {

    private int limit = 1;
    private int activeRequests;

    public synchronized void acquire() throws InterruptedException {
        while (activeRequests >= limit) {
            wait();
        }
        activeRequests++;
    }

    public synchronized void release() {
        activeRequests--;
        notifyAll();
    }

    public synchronized void updateLimit(int limit) {
        this.limit = limit;
        notifyAll();
    }

    public synchronized int getLimit() {
        return limit;
    }
}
