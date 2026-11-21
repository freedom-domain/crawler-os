package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_excluded_document")
public class AiRagExcludedDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceIndex;
    private String documentId;
    private LocalDateTime createTime;
}
