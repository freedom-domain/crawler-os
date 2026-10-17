package com.collect.finance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class HistorySyncTaskConfig {

    @Bean("historySyncExecutor")
    public Executor historySyncExecutor(
            @Value("${investment.history-sync.concurrency:1}") int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("investment.history-sync.concurrency must be at least 1");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("history-sync-");
        return executor;
    }
}
