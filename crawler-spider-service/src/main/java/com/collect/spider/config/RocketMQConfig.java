package com.collect.spider.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

@Configuration
public class RocketMQConfig {

    @Value("${spring.rocketmq.name-server}")
    private String nameServer;

    @Value("${spring.rocketmq.producer.group:default-producer-group}")
    private String producerGroup;

    @Value("${spring.rocketmq.producer.send-message-timeout:3000}")
    private int sendMsgTimeout;

    @Bean
    public RocketMQTemplate rocketMQTemplate() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendMsgTimeout);

        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(producer);
        template.setMessageConverter(new MappingJackson2MessageConverter());
        return template;
    }
}
