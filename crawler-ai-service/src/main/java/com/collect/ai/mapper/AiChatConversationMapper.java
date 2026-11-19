package com.collect.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.ai.entity.AiChatConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiChatConversationMapper extends BaseMapper<AiChatConversation> {

    @Select("""
            SELECT id, user_id, title, created_at, updated_at
            FROM ai_chat_conversation
            WHERE user_id = #{userId}
            ORDER BY updated_at DESC, id DESC
            LIMIT 100
            """)
    List<AiChatConversation> selectRecentByUserId(@Param("userId") Long userId);
}
