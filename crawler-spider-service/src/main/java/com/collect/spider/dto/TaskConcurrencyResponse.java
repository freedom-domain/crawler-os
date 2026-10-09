package com.collect.spider.dto;

import lombok.Data;

@Data
public class TaskConcurrencyResponse {
    private int maxConcurrency;
    private int urlConcurrency;
    private int retentionDays;
}
