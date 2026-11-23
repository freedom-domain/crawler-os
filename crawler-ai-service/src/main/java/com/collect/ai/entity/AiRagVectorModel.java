package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_vector_model")
public class AiRagVectorModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private String apiKey;
    private Integer dimensions;
    private Integer topK;
    /** 模型最大输入 token 数（可选），用于校验分块长度是否超出模型上下文 */
    private Integer maxTokens;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
