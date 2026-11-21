package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_assistant_knowledge_base")
public class AiAssistantKnowledgeBase {
    private Long assistantId;
    private Long knowledgeBaseId;
}
