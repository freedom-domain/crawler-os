package com.collect.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class HistorySyncTaskResponse {
    private Long id;
    private String instrumentType;
    private String status;
    private Integer totalCount;
    private Integer finishedCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer cancelledCount;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String message;
    private Integer itemPage;
    private Integer itemPageSize;
    private Integer itemTotal;
    private List<HistorySyncTaskItemResponse> items;
}
