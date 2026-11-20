package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_chat_model")
public class AiRagChatModel {

    @TableId
    private Long id;

    private String name;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private String apiKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
