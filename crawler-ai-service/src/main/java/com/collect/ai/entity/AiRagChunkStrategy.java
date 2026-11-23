package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_rag_chunk_strategy")
public class AiRagChunkStrategy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String strategyType;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private BigDecimal similarityThreshold;
    private Integer priority;
    private Integer minDocumentLength;
    private Integer maxDocumentLength;
    private String documentTypes;
    private Boolean defaultStrategy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
