package com.collect.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HistorySyncTaskItemResponse {
    private Long id;
    private String instrumentType;
    private String fundType;
    private String code;
    private String name;
    private String status;
    private String message;
    private Integer pointCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
