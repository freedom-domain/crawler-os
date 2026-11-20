package com.collect.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    @Select("""
            SELECT id, conversation_id, role, content, reasoning_content, status, sources_json, created_at
            FROM ai_chat_message
            WHERE conversation_id = #{conversationId}
            ORDER BY id ASC
            """)
    List<AiChatMessage> selectByConversationId(@Param("conversationId") Long conversationId);

    @Select("""
            SELECT id, conversation_id, role, content, reasoning_content, status, sources_json, created_at
            FROM ai_chat_message
            WHERE conversation_id = #{conversationId}
              AND status = 'SUCCESS'
              AND role IN ('user', 'assistant')
            ORDER BY id ASC
            """)
    List<AiChatMessage> selectSuccessfulContextByConversationId(
            @Param("conversationId") Long conversationId);

    @Update("""
            UPDATE ai_chat_message
            SET content = CONCAT(content, #{delta})
            WHERE id = #{messageId} AND status = 'STREAMING'
            """)
    int appendStreamingContent(
            @Param("messageId") Long messageId,
            @Param("delta") String delta);

    @Update("""
            UPDATE ai_chat_message
            SET reasoning_content = CONCAT(COALESCE(reasoning_content, ''), #{delta})
            WHERE id = #{messageId} AND status = 'STREAMING'
            """)
    int appendStreamingReasoning(
            @Param("messageId") Long messageId,
            @Param("delta") String delta);

    @Update("""
            UPDATE ai_chat_message
            SET status = 'CANCELLED', sources_json = '[]'
            WHERE id = #{messageId} AND conversation_id = #{conversationId}
              AND role = 'assistant' AND status = 'STREAMING'
            """)
    int cancelStreamingMessage(
            @Param("conversationId") Long conversationId,
            @Param("messageId") Long messageId);
}
