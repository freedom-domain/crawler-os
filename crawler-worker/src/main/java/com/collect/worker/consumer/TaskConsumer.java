package com.collect.worker.consumer;

import com.alibaba.fastjson2.JSON;
import com.collect.common.mq.MqConstants;
import com.collect.common.mq.TaskMessage;
import com.collect.worker.crawler.CrawlerEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private final CrawlerEngine crawlerEngine;

    @KafkaListener(
            topics = MqConstants.SPIDER_TASK_TOPIC
    )
    public void onMessage(String message) {
        log.info("收到爬虫任务消息: {}", message);
        try {
            TaskMessage msg = JSON.parseObject(message, TaskMessage.class);
            crawlerEngine.execute(msg);
        } catch (Exception e) {
            log.error("任务执行异常", e);
        }
    }
}
