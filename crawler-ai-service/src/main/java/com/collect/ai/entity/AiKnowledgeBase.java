package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "ai_knowledge_base", autoResultMap = true)
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
    /** ES 来源知识库限定的爬虫 ID 集合；NULL/空表示收录全部爬虫的文档 */
    @TableField(value = "spider_ids", typeHandler = JacksonTypeHandler.class,
            updateStrategy = FieldStrategy.ALWAYS)
    private List<Long> spiderIds;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
