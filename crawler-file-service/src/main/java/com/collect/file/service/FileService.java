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
        } catch (Exception e) {
            throw new BizException("文件下载失败: " + e.getMessage());
        }
    }

    public void delete(String bucket, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
                fileMetadataMapper.delete(new QueryWrapper<FileMetadata>()
                        .eq("bucket", bucket)
                        .eq("object_name", objectName));
        } catch (Exception e) {
            throw new BizException("文件删除失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("null")
    public IPage<FileMetadata> page(int current, int size, String category, Long spiderId) {
        return fileMetadataMapper.selectFilePage(new Page<>(current, size), category, spiderId);
    }
}
