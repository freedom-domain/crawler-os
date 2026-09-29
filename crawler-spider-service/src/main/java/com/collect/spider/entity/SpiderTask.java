package com.collect.spider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Long totalCostMs;
    private Integer successCount;
    private Integer failCount;
    @TableField(exist = false)
    private Integer existingCount;
    @TableField(exist = false)
    private Integer htmlSuccessCount;
    @TableField(exist = false)
    private Integer htmlFailCount;
    @TableField(exist = false)
    private Integer htmlExistingCount;
    @TableField(exist = false)
    private Integer imageSuccessCount;
    @TableField(exist = false)
    private Integer imageFailCount;
    @TableField(exist = false)
    private Integer imageExistingCount;
    private String errorMessage;
    private Long taskId;
    @JsonIgnore
    @TableField("task_message")
    private String taskMessage;
}
