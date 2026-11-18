package com.collect.finance.config;

import org.springframework.stereotype.Component;

@Component
public class HistoryRequestLimiter {

    private int limit = 1;
    private int intervalMillis = 1000;
    private int activeRequests;
    private long lastRequestNanos;

    public synchronized void acquire() throws InterruptedException {
        while (activeRequests >= limit) {
            wait();
        }
        activeRequests++;
        try {
            while (true) {
                if (lastRequestNanos == 0) {
                    lastRequestNanos = System.nanoTime();
                    return;
                }
                long remainingNanos = intervalMillis * 1_000_000L
                        - (System.nanoTime() - lastRequestNanos);
                if (remainingNanos <= 0) {
                    lastRequestNanos = System.nanoTime();
                    return;
                }
                long waitMillis = remainingNanos / 1_000_000L;
                int waitNanos = (int) (remainingNanos % 1_000_000L);
                wait(waitMillis, waitNanos);
            }
        } catch (InterruptedException exception) {
            activeRequests--;
            notifyAll();
            throw exception;
        }
    }

    public synchronized void release() {
        activeRequests--;
        notifyAll();
    }

    public synchronized void updateLimit(int limit) {
        this.limit = limit;
        notifyAll();
    }

    public synchronized void updateIntervalMillis(int intervalMillis) {
        this.intervalMillis = intervalMillis;
        notifyAll();
    }

    public synchronized int getLimit() {
        return limit;
    }

    public synchronized int getIntervalMillis() {
        return intervalMillis;
    }
}
