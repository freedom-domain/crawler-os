package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_chat_model")
public class AiRagChatModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private Integer contextLength = 8192;
    private String apiKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
