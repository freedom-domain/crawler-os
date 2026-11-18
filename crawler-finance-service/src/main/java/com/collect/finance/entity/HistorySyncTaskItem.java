package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("history_sync_task_item")
public class HistorySyncTaskItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
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
