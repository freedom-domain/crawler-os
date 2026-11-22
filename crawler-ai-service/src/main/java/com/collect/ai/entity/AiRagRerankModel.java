package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_rerank_model")
public class AiRagRerankModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String rerankProvider;
    private String rerankBaseUrl;
    private String rerankModel;
    private String apiKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
