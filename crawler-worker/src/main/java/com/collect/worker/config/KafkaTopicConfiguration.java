package com.collect.worker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfiguration {

    private static final int TASK_TOPIC_PARTITIONS = 20;

    @Bean
    public NewTopic spiderTaskTopic(@Value("${app.kafka.spider-task-topic}") String topicName) {
        return new NewTopic(topicName, TASK_TOPIC_PARTITIONS, (short) 1);
    }
}
