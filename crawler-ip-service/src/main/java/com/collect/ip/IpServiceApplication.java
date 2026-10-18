package com.collect.ip;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.collect")
@EnableDiscoveryClient
@MapperScan("com.collect.ip.mapper")
public class IpServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IpServiceApplication.class, args);
    }
}
