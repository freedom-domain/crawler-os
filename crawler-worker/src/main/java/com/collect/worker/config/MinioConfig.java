package com.collect.worker.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://127.0.0.1:9000}")
    private String endpoint;

    @Value("${minio.accessKey:admin}")
    private String accessKey;

    @Value("${minio.secretKey:admin123}")
    private String secretKey;

    /** MinIO 客户端 OkHttp 连接池：空闲连接上限。20 任务 × 50 并发同时 putObject 时，
     *  SDK 默认 5 idle 偏小，跨机房场景建议调大。 */
    @Value("${minio.pool.max-idle:64}")
    private int minioMaxIdleConnections;

    /** MinIO 客户端 OkHttp 连接池：空闲连接保留时长（分钟） */
    @Value("${minio.pool.keep-alive-minutes:5}")
    private int minioKeepAliveMinutes;

    @Bean
    public MinioClient minioClient() {
        // MinIO Java SDK 8.5.x 的异步客户端由 okhttp3 直接承载；通过 OkHttpClient.Builder
        // 显式配连接池，避免默认 5 idle 在并发上传时成为瓶颈。
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectionPool(new okhttp3.ConnectionPool(
                        Math.max(1, minioMaxIdleConnections),
                        Math.max(1, minioKeepAliveMinutes), TimeUnit.MINUTES))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(okHttpClient)
                .build();
    }
}
