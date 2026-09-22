package com.collect.search.minio;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * MinIO 读取辅助类：用于从 MinIO 读取已存储的 HTML 原文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioHelper {

    private final MinioClient minioClient;

    /**
     * 读取对象内容（调用方负责关闭返回的流）。
     */
    public InputStream getObject(String bucket, String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }

    /**
     * 读取 HTML 原文。对象不存在或读取失败时返回 null。
     */
    public String readHtml(String bucket, String objectName) {
        try (InputStream in = getObject(bucket, objectName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("从 MinIO 读取 HTML 失败: bucket={}, object={}", bucket, objectName, e);
            return null;
        }
    }
}
