package com.collect.file.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.common.exception.BizException;
import com.collect.file.entity.FileMetadata;
import com.collect.file.mapper.FileMetadataMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final FileMetadataMapper fileMetadataMapper;

    private void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket 操作失败: " + e.getMessage(), e);
        }
    }

    public FileMetadata upload(String bucket, MultipartFile file, String category, Long spiderId) {
        try {
            ensureBucket(bucket);
            String objectName = category + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + file.getOriginalFilename();
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(in, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }

            FileMetadata meta = new FileMetadata();
            meta.setBucket(bucket);
            meta.setObjectName(objectName);
            meta.setFileName(file.getOriginalFilename());
            meta.setContentType(file.getContentType());
            meta.setFileSize(file.getSize());
            meta.setCategory(category);
            meta.setSpiderId(spiderId);
            fileMetadataMapper.insert(meta);
            return meta;
        } catch (Exception e) {
            throw new BizException("文件上传失败: " + e.getMessage());
        }
    }

    public InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (ErrorResponseException e) {
            if (e.response().code() != 404) {
                throw new BizException("文件下载失败: " + e.getMessage());
            }
            // 请求的 bucket 中不存在该对象，回退到 file_metadata 中记录的 bucket
            // （兼容图片 bucket 配置变化，如 crawler-images 与 crawler-images-local）
            FileMetadata meta = fileMetadataMapper.selectByObjectName(objectName);
            if (meta != null && meta.getBucket() != null && !meta.getBucket().equals(bucket)) {
                log.info("对象在 {} 中不存在，回退到元数据记录的 bucket: {}", bucket, meta.getBucket());
                try {
                    return minioClient.getObject(GetObjectArgs.builder()
                            .bucket(meta.getBucket())
                            .object(objectName)
                            .build());
                } catch (Exception ex) {
                    throw new BizException("文件下载失败: " + ex.getMessage());
                }
            }
            throw new BizException("文件下载失败: 文件不存在 (bucket=" + bucket + ", object=" + objectName + ")");
        } catch (Exception e) {
            throw new BizException("文件下载失败: " + e.getMessage());
        }
    }

    public void delete(String bucket, String objectName) {
        try {
            deleteAllObjectVersions(bucket, objectName);
            verifyObjectDeleted(bucket, objectName);
            fileMetadataMapper.delete(new QueryWrapper<FileMetadata>()
                    .eq("bucket", bucket)
                    .eq("object_name", objectName));
        } catch (Exception e) {
            throw new BizException("文件删除失败: " + e.getMessage());
        }
    }

    public int deleteByCondition(String category, Long spiderId, String title) {
        QueryWrapper<FileMetadata> query = new QueryWrapper<>();
        if (category != null && !category.isBlank()) {
            query.eq("category", category);
        }
        if (spiderId != null) {
            query.eq("spider_id", spiderId);
        }
        if (title != null && !title.isBlank()) {
            query.like("title", title);
        }
        int deleted = 0;
        for (FileMetadata metadata : fileMetadataMapper.selectList(query)) {
            delete(metadata.getBucket(), metadata.getObjectName());
            deleted++;
        }
        return deleted;
    }

    private void deleteAllObjectVersions(String bucket, String objectName) throws Exception {
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
    }

    private void verifyObjectDeleted(String bucket, String objectName) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            throw new BizException("MinIO 文件删除校验失败");
        } catch (ErrorResponseException e) {
            if (e.response().code() != 404) {
                throw e;
            }
        }
    }

    @SuppressWarnings("null")
    public IPage<FileMetadata> page(int current, int size, String category, Long spiderId, String title) {
        return fileMetadataMapper.selectFilePage(new Page<>(current, size), category, spiderId, title);
    }
}
