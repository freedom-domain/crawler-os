package com.collect.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_metadata")
public class FileMetadata extends BaseEntity {

    private String bucket;
    private String objectName;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String category;
    private Long spiderId;
}
