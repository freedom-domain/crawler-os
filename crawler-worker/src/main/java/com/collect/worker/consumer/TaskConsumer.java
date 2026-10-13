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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka 任务消费者。
 *
 * <p>用「槽位 + 看门狗」模式控制全局最大并发任务数：
 * <ul>
 *   <li>每个任务占用 1 个槽位，槽位满时新任务阻塞等待（背压，由 Kafka 分区积压体现）；</li>
 *   <li>每个任务附带一个看门狗超时（{@code app.task.max-task-duration-seconds}，默认 2 小时）：
 *       若任务因卡死线程等原因超期未完成，看门狗强制释放槽位，防止槽位耗尽后 worker 彻底停摆。</li>
 * </ul>
 *
 * <p>看门狗只释放「任务槽位」这一逻辑资源，不会中断卡死的旧任务线程（旧线程仍占虚拟线程，
 * 但不再阻止新任务进入）。超时阈值应设为业务上合理的最长爬取时长，避免误杀正常长任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private final CrawlerEngine crawlerEngine;

    @Value("${app.kafka.spider-task-consumer-concurrency:20}")
    private int maxConcurrentTasks;
    /** 单任务最长执行时长（秒）：看门狗超时阈值，超期强制释放槽位。0 表示禁用看门狗。 */
    @Value("${app.task.max-task-duration-seconds:7200}")
    private long maxTaskDurationSeconds;

    private final Object slotLock = new Object();
    private int runningTasks = 0;
    private final ExecutorService taskExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    @PostConstruct
    void init() {
        log.info("任务消费者初始化: 最大并发任务数={}, 单任务最长时长={}s", maxConcurrentTasks, maxTaskDurationSeconds);
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

        // 任务槽位：每个任务占 1 个，满时阻塞等待（背压）
        synchronized (slotLock) {
            while (runningTasks >= maxConcurrentTasks) {
                log.info("任务槽位已满，等待: taskId={}, running={}/{}", msg.getTaskId(), runningTasks, maxConcurrentTasks);
                try {
                    slotLock.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("等待任务槽位被中断: taskId={}", msg.getTaskId());
                    return;
                }
            }
            runningTasks++;
        }

        // 看门狗：超时强制释放槽位，防止卡死任务耗尽槽位后 worker 停摆
        final Long taskId = msg.getTaskId();
        final AtomicBoolean slotReleased = new AtomicBoolean(false);

        taskExecutor.submit(() -> {
            try {
                crawlerEngine.execute(msg);
            } catch (Exception e) {
                log.error("任务执行异常: taskId={}", taskId, e);
            } finally {
                releaseSlot(taskId, slotReleased);
            }
        });

        // 看门狗超时守护（maxTaskDurationSeconds <= 0 时禁用）
        if (maxTaskDurationSeconds > 0) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(maxTaskDurationSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!slotReleased.get()) {
                    log.error("任务看门狗超时（{}s），强制释放槽位: taskId={}", maxTaskDurationSeconds, taskId);
                    releaseSlot(taskId, slotReleased);
                }
            }, taskExecutor);
        }
    }

    /**
     * 释放任务槽位。用 {@link AtomicBoolean} 防止「正常完成释放」与「看门狗超时释放」重复扣减。
     */
    private void releaseSlot(Long taskId, AtomicBoolean slotReleased) {
        if (!slotReleased.compareAndSet(false, true)) {
            return; // 已释放（看门狗或正常完成其中之一先到）
        }
        synchronized (slotLock) {
            if (runningTasks > 0) {
                runningTasks--;
            }
            slotLock.notifyAll();
        }
        log.info("释放任务槽位: taskId={}, running={}", taskId, runningTasks);
    }
}
