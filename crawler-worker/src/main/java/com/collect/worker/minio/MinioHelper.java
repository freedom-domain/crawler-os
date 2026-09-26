package com.collect.worker.minio;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    public String putJs(String bucket, String objectName, byte[] data) {
        try {
            ensureBucket(bucket);
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(in, data.length, -1)
                    .contentType("application/javascript")
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO JS 上传失败: " + e.getMessage(), e);
        }
    }

    public String putCss(String bucket, String objectName, byte[] data) {
        try {
            ensureBucket(bucket);
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(in, data.length, -1)
                    .contentType("text/css")
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO CSS 上传失败: " + e.getMessage(), e);
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

    /**
     * 读取对象内容（调用方负责关闭返回的流）。
     */
    public java.io.InputStream getObject(String bucket, String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }

    public String getHtmlIfExists(String bucket, String objectName) {
        try (java.io.InputStream in = getObject(bucket, objectName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return null;
            }
            throw new RuntimeException("MinIO HTML 缓存读取失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("MinIO HTML 缓存读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出 bucket 中指定前缀下的所有对象名。
     */
    public List<String> listObjectNames(String bucket, String prefix) throws Exception {
        List<String> names = new ArrayList<>();
        for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build())) {
            names.add(result.get().objectName());
        }
        return names;
    }

    /**
     * 同 bucket 内复制对象（服务端 copy，不经过本地）。
     */
    public void copyObject(String bucket, String srcObject, String dstObject) throws Exception {
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(bucket)
                .object(dstObject)
                .source(io.minio.CopySource.builder()
                        .bucket(bucket)
                        .object(srcObject)
                        .build())
                .build());
    }

    /**
     * 删除对象。
     */
    public void removeObject(String bucket, String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }
}
