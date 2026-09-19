package com.collect.worker.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioHelper {

    private final MinioClient minioClient;

    public void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("创建 MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket 操作失败: " + e.getMessage(), e);
        }
    }

    public String putHtml(String bucket, String objectName, String html) {
        try {
            ensureBucket(bucket);
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(in, bytes.length, -1)
                    .contentType("text/html; charset=utf-8")
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 上传失败: " + e.getMessage(), e);
        }
    }

    public String putImage(String bucket, String objectName, byte[] data, String contentType) {
        try {
            ensureBucket(bucket);
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(in, data.length, -1)
                    .contentType(contentType)
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 图片上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断对象是否已存在（用于跳过重复下载）。
     */
    public boolean objectExists(String bucket, String objectName) {
        try {
            statObject(bucket, objectName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public StatObjectResponse statObject(String bucket, String objectName) throws Exception {
        return minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }
}
