package com.collect.worker.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider_task_log")
public class SpiderTaskLog extends BaseEntity {

    private Long taskId;
    private Long spiderId;
    private String url;
    private Integer status;
    private String level;
    private String message;
    private Integer costMs;
}
