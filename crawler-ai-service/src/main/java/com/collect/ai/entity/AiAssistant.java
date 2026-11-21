package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_assistant")
public class AiAssistant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String avatar;
    private String systemPrompt;
    private Boolean enabled;
    private Long knowledgeBaseId;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
