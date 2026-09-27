package com.collect.search.minio;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
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
        try {
            if (!objectExists(bucket, objectName)) {
                return null;
            }
        } catch (Exception e) {
            log.warn("迁移 MinIO HTML 缓存失败: bucket={}, object={}", bucket, objectName, e);
            return null;
        }
        try (InputStream in = getObject(bucket, objectName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("从 MinIO 读取 HTML 失败: bucket={}, object={}", bucket, objectName, e);
            return null;
        }
    }

    private boolean objectExists(String bucket, String objectName) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            String errorCode = e.errorResponse().code();
            if ("NoSuchKey".equals(errorCode) || "NoSuchBucket".equals(errorCode)) {
                return false;
            }
            throw e;
        }
    }

    public void removeObject(String bucket, String objectName) throws Exception {
        boolean found = false;
        for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(objectName)
                .includeVersions(true)
                .build())) {
            Item item = result.get();
            if (!objectName.equals(item.objectName())) {
                continue;
            }
            found = true;
            RemoveObjectArgs.Builder builder = RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName);
            if (item.versionId() != null && !item.versionId().isBlank()) {
                builder.versionId(item.versionId());
            }
            minioClient.removeObject(builder.build());
        }
        if (!found) {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        }
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            throw new IllegalStateException("MinIO 对象删除校验失败: " + objectName);
        } catch (ErrorResponseException e) {
            if (e.response().code() != 404) {
                throw e;
            }
        }
    }
}
