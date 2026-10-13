package com.onepage.im.mapper;

import com.onepage.im.dto.MessageHit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageMapper {
  @Insert("""
      INSERT INTO message (id, conversation_id, sender_id, msg_type, content, created_at)
      VALUES (#{id}, #{conversationId}, #{senderId}, #{msgType}, CAST(#{content} AS JSON), #{createdAt})
      """)
  int insert(
      @Param("id") long id,
      @Param("conversationId") long conversationId,
      @Param("senderId") long senderId,
      @Param("msgType") String msgType,
      @Param("content") String content,
      @Param("createdAt") LocalDateTime createdAt);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT id, conversation_id, sender_id, msg_type, CAST(content AS CHAR) AS content, created_at
      FROM message
      WHERE conversation_id = #{conversationId}
        <if test="beforeId != null">
          AND id &lt; #{beforeId}
        </if>
      ORDER BY id DESC
      LIMIT #{limit}
      </script>
      """)
  List<MessageHit> page(
      @Param("conversationId") long conversationId,
      @Param("beforeId") Long beforeId,
      @Param("limit") int limit);

  @Select("""
      SELECT IFNULL(SUM(unread), 0) FROM (
        SELECT COUNT(*) AS unread
        FROM conversation_member me
        JOIN message um ON um.conversation_id = me.conversation_id
          AND um.sender_id <> me.user_id
          AND um.id > IFNULL(me.last_read_message_id, 0)
        WHERE me.user_id = #{userId}
        GROUP BY me.conversation_id
      ) t
      """)
  long countUnread(@Param("userId") long userId);
}
