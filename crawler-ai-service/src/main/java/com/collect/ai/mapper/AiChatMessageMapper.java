package com.collect.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    @Select("""
            SELECT id, conversation_id, role, content, status, sources_json, created_at
            FROM ai_chat_message
            WHERE conversation_id = #{conversationId}
            ORDER BY id ASC
            """)
    List<AiChatMessage> selectByConversationId(@Param("conversationId") Long conversationId);
}
