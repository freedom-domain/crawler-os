package com.collect.common.mq;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务日志消息体：worker 抓取过程中产生，发送到 Kafka 后由消费端批量入库。
 */
@Data
public class TaskLogMessage implements Serializable {

    private Long taskId;
    private Long spiderId;
    private String url;
    /** 0失败 1成功 2跳过 */
    private Integer status;
    /** INFO / ERROR */
    private String level;
    /** html / image / js */
    private String type;
    private String message;
    private Integer costMs;
    /** 产生时间（worker 端），入库时作为 create_time */
    private LocalDateTime createTime;
}
