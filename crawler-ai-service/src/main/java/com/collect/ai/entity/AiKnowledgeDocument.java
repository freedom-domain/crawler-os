package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_document")
public class AiKnowledgeDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeBaseId;
    private String title;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String minioBucket;
    private String objectName;
    private String sourceDocumentId;
    private Boolean vectorized;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
