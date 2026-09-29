package com.collect.worker.consumer;

import com.alibaba.fastjson2.JSON;
import com.collect.common.mq.TaskLogMessage;
import com.collect.worker.entity.SpiderTaskLog;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务日志消费者：批量消费 worker 发出的日志消息并入库。
 * 独立消费组，与任务消费组互不影响。
 * topic 通过 app.kafka.task-log-topic 配置（各环境 yml 已带环境后缀，如 spider_task_log_topic-local）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskLogConsumer {

    private static final int BATCH_SIZE = 500;

    private final SpiderTaskLogMapper logMapper;

    @KafkaListener(
            topics = "${app.kafka.task-log-topic}",
            groupId = "${app.kafka.task-log-consumer-group:task-log-consumer-group}",
            batch = "true",
            concurrency = "${app.kafka.task-log-consumer-concurrency:4}"
    )
    public void onMessage(@Payload List<String> messages) {
        List<SpiderTaskLog> batch = new ArrayList<>(messages.size());
        for (String message : messages) {
            try {
                TaskLogMessage msg = JSON.parseObject(message, TaskLogMessage.class);
                if (msg == null || msg.getTaskId() == null) {
                    log.warn("忽略无效的任务日志消息: {}", message);
                    continue;
                }
                SpiderTaskLog logEntry = new SpiderTaskLog();
                logEntry.setTaskId(msg.getTaskId());
                logEntry.setSpiderId(msg.getSpiderId());
                logEntry.setUrl(msg.getUrl());
                logEntry.setStatus(msg.getStatus());
                logEntry.setLevel(msg.getLevel());
                logEntry.setType(msg.getType());
                logEntry.setMessage(msg.getMessage());
                logEntry.setCostMs(msg.getCostMs());
                if (msg.getCreateTime() != null) {
                    logEntry.setCreateTime(msg.getCreateTime());
                }
                batch.add(logEntry);
            } catch (Exception e) {
                log.error("任务日志消息解析失败: {}", message, e);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < batch.size(); i += BATCH_SIZE) {
                List<SpiderTaskLog> sub = batch.subList(i, Math.min(i + BATCH_SIZE, batch.size()));
                sub.forEach(logMapper::insert);
            }
            log.debug("任务日志入库 {} 条", batch.size());
        } catch (Exception e) {
            // 入库失败抛出异常，触发 Kafka 重试（默认 max.in.flight / 重试策略），避免日志丢失
            log.error("任务日志批量入库失败，共 {} 条，将触发重试", batch.size(), e);
            throw new RuntimeException("任务日志批量入库失败", e);
        }
    }
}
