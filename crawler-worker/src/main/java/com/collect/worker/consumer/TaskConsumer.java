package com.collect.worker.consumer;

import com.alibaba.fastjson2.JSON;
import com.collect.common.mq.TaskMessage;
import com.collect.worker.crawler.CrawlerEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private final CrawlerEngine crawlerEngine;

    @Value("${app.kafka.spider-task-consumer-concurrency:20}")
    private int maxConcurrentTasks;

    private Semaphore taskSlots;
    private final ExecutorService taskExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    @PostConstruct
    void init() {
        taskSlots = new Semaphore(maxConcurrentTasks, true);
        log.info("任务消费者初始化: 最大并发任务数={}", maxConcurrentTasks);
    }

    @KafkaListener(
            topics = "${app.kafka.spider-task-topic}",
            concurrency = "${app.kafka.spider-task-consumer-concurrency:20}"
    )
    public void onMessage(String message) {
        log.info("收到爬虫任务消息: {}", message);
        TaskMessage msg;
        try {
            msg = JSON.parseObject(message, TaskMessage.class);
        } catch (Exception e) {
            log.error("任务消息解析失败: {}", message, e);
            return;
        }
        try {
            taskSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待任务槽位被中断: taskId={}", msg.getTaskId());
            return;
        }
        taskExecutor.submit(() -> {
            try {
                crawlerEngine.execute(msg);
            } catch (Exception e) {
                log.error("任务执行异常: taskId={}", msg.getTaskId(), e);
            } finally {
                taskSlots.release();
            }
        });
    }
}
