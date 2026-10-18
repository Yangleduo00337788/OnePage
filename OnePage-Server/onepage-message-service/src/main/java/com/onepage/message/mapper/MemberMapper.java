package com.onepage.message.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MemberMapper {
  @Insert("""
      INSERT INTO conversation_member (conversation_id, user_id, last_read_message_id, joined_at)
      VALUES (#{conversationId}, #{userId}, NULL, #{now})
      """)
  int insert(
      @Param("conversationId") long conversationId,
      @Param("userId") long userId,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT user_id FROM conversation_member
      WHERE conversation_id = #{conversationId} AND user_id <> #{userId}
      LIMIT 1
      """)
  Long findPeerId(@Param("conversationId") long conversationId, @Param("userId") long userId);

  @Update("""
      UPDATE conversation_member
      SET last_read_message_id = #{messageId}
      WHERE conversation_id = #{conversationId} AND user_id = #{userId}
        AND (last_read_message_id IS NULL OR last_read_message_id < #{messageId})
      """)
  int markRead(
      @Param("conversationId") long conversationId,
      @Param("userId") long userId,
      @Param("messageId") long messageId);
}
