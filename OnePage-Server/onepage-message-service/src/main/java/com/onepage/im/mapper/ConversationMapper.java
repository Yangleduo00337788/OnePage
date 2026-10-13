package com.onepage.im.mapper;

import com.onepage.im.dto.ConversationHit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConversationMapper {
  @Insert("""
      INSERT INTO conversation (id, type, pair_key, created_at, updated_at)
      VALUES (#{id}, 'single', #{pairKey}, #{now}, #{now})
      """)
  int insert(
      @Param("id") long id,
      @Param("pairKey") String pairKey,
      @Param("now") LocalDateTime now);

  @Select("SELECT id FROM conversation WHERE pair_key = #{pairKey}")
  Long findIdByPair(@Param("pairKey") String pairKey);

  @Select("""
      SELECT c.id
      FROM conversation c
      JOIN conversation_member me ON me.conversation_id = c.id AND me.user_id = #{userId}
      WHERE c.id = #{conversationId}
      """)
  Long requireMember(@Param("conversationId") long conversationId, @Param("userId") long userId);

  @Update("UPDATE conversation SET updated_at = #{now} WHERE id = #{id}")
  int touch(@Param("id") long id, @Param("now") LocalDateTime now);

  @Select("""
      SELECT
        c.id AS conversationId,
        peer.user_id AS peerId,
        me.last_read_message_id AS lastReadId,
        c.updated_at AS updatedAt,
        lm.id AS lastMessageId,
        lm.msg_type AS lastMsgType,
        CAST(lm.content AS CHAR) AS lastContent,
        lm.created_at AS lastCreatedAt,
        (SELECT COUNT(*) FROM message um
          WHERE um.conversation_id = c.id
            AND um.sender_id <> #{userId}
            AND um.id > IFNULL(me.last_read_message_id, 0)
        ) AS unreadCount
      FROM conversation c
      JOIN conversation_member me ON me.conversation_id = c.id AND me.user_id = #{userId}
      JOIN conversation_member peer ON peer.conversation_id = c.id AND peer.user_id <> #{userId}
      LEFT JOIN message lm ON lm.id = (
        SELECT MAX(id) FROM message WHERE conversation_id = c.id
      )
      ORDER BY c.updated_at DESC, c.id DESC
      """)
  List<ConversationHit> listForUser(@Param("userId") long userId);
}
