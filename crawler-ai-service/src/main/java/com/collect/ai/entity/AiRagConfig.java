package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_config")
public class AiRagConfig {

    @TableId
    private String environment;

    private Boolean enabled;
    private String sourceIndex;
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private Integer dimensions;
    private Long vectorModelId;
    private Long chatModelId;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private String apiKey;
    private Integer topK;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String vectorIndex;
    private String vectorIndexPrefix;
    private String vectorFieldPrefix;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
