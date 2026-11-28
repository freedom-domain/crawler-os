package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_assistant_role")
public class AiAssistantRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private Boolean enabled;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
