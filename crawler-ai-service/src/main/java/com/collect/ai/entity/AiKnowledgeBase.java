package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_base")
public class AiKnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Boolean enabled;
    private Long vectorModelId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long rerankModelId;
    private String sourceIndex;
    private String vectorIndex;
    private String vectorField;
    private String sourceType;
    private String uploadBucket;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
