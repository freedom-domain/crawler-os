package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("history_sync_task")
public class HistorySyncTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String instrumentType;
    private String status;
    private Integer totalCount;
    private Integer finishedCount;
    private Integer successCount;
    private Integer failedCount;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
