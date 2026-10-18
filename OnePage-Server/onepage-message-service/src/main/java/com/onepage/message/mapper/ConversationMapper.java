package com.onepage.message.mapper;

import com.onepage.message.dto.ConversationHit;
import com.onepage.message.dto.OpsConversationHit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

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
            AND um.deleted_at IS NULL
            AND um.id > IFNULL(me.last_read_message_id, 0)
        ) AS unreadCount
      FROM conversation c
      JOIN conversation_member me ON me.conversation_id = c.id AND me.user_id = #{userId}
      JOIN conversation_member peer ON peer.conversation_id = c.id AND peer.user_id <> #{userId}
      LEFT JOIN message lm ON lm.id = (
        SELECT MAX(id) FROM message WHERE conversation_id = c.id AND deleted_at IS NULL
      )
      ORDER BY c.updated_at DESC, c.id DESC
      """)
  List<ConversationHit> listForUser(@Param("userId") long userId);

  @Select("SELECT id FROM conversation WHERE id = #{id}")
  Long findId(@Param("id") long id);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT
        c.id AS conversationId,
        a.user_id AS leftId,
        b.user_id AS rightId,
        c.updated_at AS updatedAt,
        lm.id AS lastMessageId,
        lm.msg_type AS lastMsgType,
        CAST(lm.content AS CHAR) AS lastContent
      FROM conversation c
      JOIN conversation_member a ON a.conversation_id = c.id
      JOIN conversation_member b ON b.conversation_id = c.id AND b.user_id &gt; a.user_id
      LEFT JOIN message lm ON lm.id = (
        SELECT MAX(id) FROM message WHERE conversation_id = c.id
      )
      WHERE 1 = 1
        <if test="userId != null">
          AND (a.user_id = #{userId} OR b.user_id = #{userId} OR c.id = #{userId})
        </if>
        <if test="beforeUpdated != null">
          AND (
            c.updated_at &lt; #{beforeUpdated}
            OR (c.updated_at = #{beforeUpdated} AND c.id &lt; #{beforeId})
          )
        </if>
      ORDER BY c.updated_at DESC, c.id DESC
      LIMIT #{limit}
      </script>
      """)
  List<OpsConversationHit> opsPage(
      @Param("userId") Long userId,
      @Param("beforeUpdated") LocalDateTime beforeUpdated,
      @Param("beforeId") Long beforeId,
      @Param("limit") int limit);
}
