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

import java.io.InputStream;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public R<FileMetadata> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "crawler") String bucket,
                                  @RequestParam(defaultValue = "file") String category,
                                  @RequestParam(required = false) Long spiderId) {
        return R.ok(fileService.upload(bucket, file, category, spiderId));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam String bucket,
                                                        @RequestParam String objectName) {
        InputStream in = fileService.download(bucket, objectName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + objectName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(in));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping
    public R<Void> delete(@RequestParam String bucket, @RequestParam String objectName) {
        fileService.delete(bucket, objectName);
        return R.ok();
    }

    @Operation(summary = "文件分页列表")
    @GetMapping("/page")
    public R<IPage<FileMetadata>> page(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String category) {
        return R.ok(fileService.page(current, size, category));
    }
}
