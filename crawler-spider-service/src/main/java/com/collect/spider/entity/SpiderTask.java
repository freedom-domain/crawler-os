package com.collect.spider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider_task")
public class SpiderTask extends BaseEntity {

    private Long spiderId;
    private String spiderName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long totalCostMs;
    private Integer successCount;
    private Integer failCount;
    private String errorMessage;
    private Long taskId;
}
