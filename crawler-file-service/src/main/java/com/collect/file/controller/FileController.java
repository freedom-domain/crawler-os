package com.collect.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.collect.common.result.R;
import com.collect.file.entity.FileMetadata;
import com.collect.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Value("${minio.file-bucket:crawler-files}")
    private String fileBucket;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public R<FileMetadata> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "bucket", required = false) String bucket,
                                   @RequestParam(value = "category", defaultValue = "file") String category,
                                   @RequestParam(value = "spiderId", required = false) Long spiderId,
                                   @RequestParam(value = "source", required = false) String source) {
        if (bucket == null || bucket.isBlank()) {
            bucket = fileBucket;
        }
        return R.ok(fileService.upload(bucket, file, category, spiderId, source));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam("bucket") String bucket,
                                                         @RequestParam("objectName") String objectName) {
        InputStream in = fileService.download(bucket, objectName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + objectName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(in));
    }

    @Operation(summary = "内联访问图片（用于浏览器预览）")
    @GetMapping("/image")
    public ResponseEntity<InputStreamResource> image(@RequestParam("bucket") String bucket,
                                                     @RequestParam("objectName") String objectName) {
        InputStream in = fileService.download(bucket, objectName);
        String ext = objectName.contains(".") ? objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase() : "";
        MediaType mediaType = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(mediaType)
                .body(new InputStreamResource(in));
    }

    @Operation(summary = "内联访问静态资源")
    @GetMapping("/resource")
    public ResponseEntity<InputStreamResource> resource(@RequestParam("bucket") String bucket,
                                                         @RequestParam("objectName") String objectName) {
        InputStream in = fileService.download(bucket, objectName);
        MediaType mediaType = switch (objectName.substring(objectName.lastIndexOf('.') + 1).toLowerCase()) {
            case "js", "mjs" -> MediaType.parseMediaType("application/javascript");
            case "css" -> MediaType.parseMediaType("text/css");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(mediaType)
                .body(new InputStreamResource(in));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping
    public R<Void> delete(@RequestParam("bucket") String bucket, @RequestParam("objectName") String objectName) {
        fileService.delete(bucket, objectName);
        return R.ok();
    }

    @Operation(summary = "按条件删除文件")
    @DeleteMapping("/condition")
    public R<Integer> deleteByCondition(@RequestParam(value = "category", required = false) String category,
                                         @RequestParam(value = "spiderId", required = false) Long spiderId,
                                         @RequestParam(value = "fileName", required = false) String fileName,
                                         @RequestParam(value = "title", required = false) String title) {
        return R.ok(fileService.deleteByCondition(category, spiderId, fileName, title));
    }

    @Operation(summary = "文件分页列表")
    @GetMapping("/page")
    public R<IPage<FileMetadata>> page(@RequestParam(value = "current", defaultValue = "1") int current,
                                        @RequestParam(value = "size", defaultValue = "10") int size,
                                        @RequestParam(value = "category", required = false) String category,
                                        @RequestParam(value = "spiderId", required = false) Long spiderId,
                                        @RequestParam(value = "fileName", required = false) String fileName,
                                        @RequestParam(value = "title", required = false) String title) {
        return R.ok(fileService.page(current, size, category, spiderId, fileName, title));
    }
}
