package com.collect.spider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.collect")
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.collect.spider.mapper")
public class SpiderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpiderServiceApplication.class, args);
    }
}
