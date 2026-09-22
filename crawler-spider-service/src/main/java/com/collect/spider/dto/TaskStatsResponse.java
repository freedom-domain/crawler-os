package com.collect.spider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskStatsResponse {

    private long total;
    private long success;
}
