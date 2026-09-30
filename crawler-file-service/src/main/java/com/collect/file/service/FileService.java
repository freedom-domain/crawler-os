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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.awt.image.RenderedImage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    public FileMetadata upload(String bucket, MultipartFile file, String category, Long spiderId, String source) {
        try {
            if (source != null && !source.isBlank()) {
                FileMetadata existing = fileMetadataMapper.selectBySource(source);
                if (existing != null) {
                    return existing;
                }
            }
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
            meta.setSource(source);
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

    public int deleteByCondition(String category, Long spiderId, String fileName, String title) {
        QueryWrapper<FileMetadata> query = new QueryWrapper<>();
        if (category != null && !category.isBlank()) {
            query.eq("category", category);
        }
        if (spiderId != null) {
            query.eq("spider_id", spiderId);
        }
        if (fileName != null && !fileName.isBlank()) {
            query.like("file_name", fileName);
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

    public boolean exists(String bucket, String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (e.response().code() == 404) {
                return false;
            }
            throw new BizException("检查文件是否存在失败: " + e.getMessage());
        } catch (Exception e) {
            throw new BizException("检查文件是否存在失败: " + e.getMessage());
        }
    }

    /**
     * 获取图片缩略图：
     * 1. 原图不存在则返回 null；
     * 2. 缩略图（thumbnail/<原图 objectName>）已存在则直接返回其输入流；
     * 3. 否则从原图生成缩略图，存入缩略图 bucket 的 thumbnail 目录（对象名与原图相同）后返回。
     *
     * @param sourceBucket   原图所在 bucket
     * @param objectName     原图对象名（含路径，不含 bucket）
     * @param thumbnailBucket 缩略图所在 bucket
     * @param maxWidth       缩略图最大宽度（像素），小于该宽度的图片不缩放
     * @return 缩略图输入流（调用方需关闭），原图不是有效图片或不存在时为 null
     */
    public InputStream getThumbnail(String sourceBucket, String objectName,
                                    String thumbnailBucket, int maxWidth) {
        // 缩略图 key：thumbnail/<width>/<原图去掉 images/ 前缀的路径>
        // 同一张图不同宽度各存一份，互不覆盖
        String base = objectName.startsWith("images/")
                ? objectName.substring("images/".length())
                : objectName;
        String thumbKey = "thumbnail/" + maxWidth + "/" + base;
        ensureBucket(thumbnailBucket);
        // 1. 缩略图已存在直接返回
        if (exists(thumbnailBucket, thumbKey)) {
            return download(thumbnailBucket, thumbKey);
        }
        // 2. 查找原图
        if (!exists(sourceBucket, objectName)) {
            return null; // 原图不存在，返回 404
        }
        try {
            byte[] original;
            try (InputStream in = download(sourceBucket, objectName)) {
                original = in.readAllBytes();
            }
            byte[] resized = resizeImageBytes(original, objectName, maxWidth);
            if (resized == null) {
                return null;
            }
            // 3. 生成缩略图并存入 MinIO
            String actualExt = actualThumbExt(objectName, resized);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(thumbnailBucket)
                    .object(thumbKey)
                    .stream(new ByteArrayInputStream(resized), resized.length, -1)
                    .contentType(resolveImageMediaType(actualExt).toString())
                    .build());
            return new ByteArrayInputStream(resized);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("生成缩略图失败: " + e.getMessage());
        }
    }

    /**
     * 将图片缩放到 maxWidth 以内（保持宽高比）。源图不可解码或宽度已小于 maxWidth 时返回 null。
     *
     * 输出格式判定：优先按"源图实际格式"，回退到"文件名扩展名"。
     * - 源图是 PNG → 输出 PNG（保留透明通道）
     * - 源图是 JPEG/其他 → 输出 JPEG（白底填充，避免透明变黑）
     *
     * 为规避 16 位 / 高动态范围 / 带色票 PNG 在 ImageIO 解码或 drawImage 时的异常，
     * 先归一化到 8 位 ARGB 再缩放。
     */
    private byte[] resizeImageBytes(byte[] original, String objectName, int maxWidth) throws IOException {
        Image src = ImageIO.read(new ByteArrayInputStream(original));
        if (src == null || src.getWidth(null) <= 0) {
            return null;
        }
        int w = src.getWidth(null);
        int h = src.getHeight(null);
        if (w <= maxWidth) {
            return null;
        }
        int newW = maxWidth;
        int newH = Math.max(1, (int) Math.round((double) maxWidth * h / w));

        // 归一化到 8 位 ARGB：兼容 16 位/高色深/色票+透明 等 ImageIO 易出问题的源图
        BufferedImage norm = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ng = norm.createGraphics();
        ng.drawImage(src, 0, 0, null);
        ng.dispose();

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(norm, 0, 0, newW, newH, null);
        g.dispose();

        // 判断是否需保留透明：采样像素，含透明则输出 PNG，否则输出 JPEG 白底
        boolean hasAlpha = sourceHasTransparency(norm);
        String format = hasAlpha ? "png" : "jpg";
        if (!hasAlpha) {
            BufferedImage rgb = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D rg = rgb.createGraphics();
            rg.setColor(java.awt.Color.WHITE);
            rg.fillRect(0, 0, newW, newH);
            rg.drawImage(scaled, 0, 0, null);
            rg.dispose();
            scaled = rgb;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(scaled, format, out);
        return out.toByteArray();
    }

    /**
     * 采样判断归一化后的图是否真的含透明像素（全不透明则按 jpg 输出，避免无谓的 png 体积）。
     */
    private boolean sourceHasTransparency(BufferedImage img) {
        int step = Math.max(1, img.getWidth() / 32);
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                if ((img.getRGB(x, y) >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    private String objectExt(String objectName) {
        if (objectName == null || !objectName.contains(".")) {
            return "";
        }
        return objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase();
    }

    public MediaType thumbnailContentType(String objectName) {
        return resolveImageMediaType(objectName);
    }

    /**
     * 根据缩放/缩略图输出字节的真实 magic bytes 返回 MediaType，
     * 避免 content-type 与实际格式不符导致浏览器无法渲染。
     */
    public MediaType resizedContentType(byte[] data) {
        return resolveImageMediaType(actualThumbExt(null, data));
    }

    /**
     * 根据缩略图字节流的真实 magic bytes 判定扩展名，
     * 避免文件名扩展名（如 .jpg 但内容是 png）导致 content-type 与实际不符。
     */
    private String actualThumbExt(String objectName, byte[] data) {
        if (data == null || data.length < 4) {
            return objectExt(objectName);
        }
        // PNG: 89 50 4E 47
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "png";
        }
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // GIF: "GIF8"
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return "gif";
        }
        // WEBP: "RIFF"...."WEBP"
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data.length >= 12
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "webp";
        }
        // BMP: "BM"
        if (data[0] == 'B' && data[1] == 'M') {
            return "bmp";
        }
        return objectExt(objectName);
    }

    private MediaType resolveImageMediaType(String objectName) {
        return switch (objectExt(objectName)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    public byte[] resizeImage(String bucket, String objectName, int maxWidth) {
        try (InputStream in = download(bucket, objectName)) {
            return resizeImageBytes(in.readAllBytes(), objectName, maxWidth);
        } catch (Exception e) {
            log.warn("图片缩放失败，返回原图: objectName={}", objectName, e);
            return null;
        }
    }

    @SuppressWarnings("null")
    public IPage<FileMetadata> page(int current, int size, String category, Long spiderId, String fileName, String title) {
        current = Math.max(1, current);
        size = Math.min(Math.max(1, size), 100);
        return fileMetadataMapper.selectFilePage(new Page<>(current, size), category, spiderId, fileName, title);
    }
}
