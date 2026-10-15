package com.onepage.notify.mapper;

import com.onepage.notify.dto.TypeCount;
import com.onepage.notify.entity.NotificationRow;
import org.apache.ibatis.annotations.Delete;
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
public interface NotificationMapper {
  @Insert("""
      INSERT INTO notification
        (id, user_id, type, actor_id, aggregate_key, post_id, comment_id, payload, read_at, created_at)
      VALUES
        (#{id}, #{userId}, #{type}, #{actorId}, #{aggregateKey}, #{postId}, #{commentId}, #{payload}, NULL, #{createdAt})
      ON DUPLICATE KEY UPDATE
        type = VALUES(type),
        actor_id = VALUES(actor_id),
        post_id = VALUES(post_id),
        comment_id = VALUES(comment_id),
        payload = VALUES(payload),
        read_at = NULL,
        created_at = VALUES(created_at)
      """)
  int upsert(
      @Param("id") long id,
      @Param("userId") long userId,
      @Param("type") String type,
      @Param("actorId") Long actorId,
      @Param("aggregateKey") String aggregateKey,
      @Param("postId") Long postId,
      @Param("commentId") Long commentId,
      @Param("payload") String payload,
      @Param("createdAt") LocalDateTime createdAt);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT id, user_id, type, actor_id, aggregate_key, post_id, comment_id, payload, read_at, created_at
      FROM notification
      WHERE user_id = #{userId}
        <if test="types != null and types.size() &gt; 0">
          AND type IN
          <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
        </if>
        <if test="unreadOnly">
          AND read_at IS NULL
        </if>
        <if test="cursorAt != null">
          AND (created_at &lt; #{cursorAt}
            OR (created_at = #{cursorAt} AND id &lt; #{cursorId}))
        </if>
      ORDER BY created_at DESC, id DESC
      LIMIT #{limit}
      </script>
      """)
  List<NotificationRow> page(
      @Param("userId") long userId,
      @Param("types") List<String> types,
      @Param("unreadOnly") boolean unreadOnly,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND read_at IS NULL")
  long countUnread(@Param("userId") long userId);

  @Select("""
      SELECT type, COUNT(*) AS cnt
      FROM notification
      WHERE user_id = #{userId} AND read_at IS NULL
      GROUP BY type
      """)
  List<TypeCount> countUnreadByType(@Param("userId") long userId);

  @Select("""
      SELECT id, user_id, type, actor_id, aggregate_key, post_id, comment_id, payload, read_at, created_at
      FROM notification
      WHERE id = #{id}
      """)
  NotificationRow findById(@Param("id") long id);

  @Delete("DELETE FROM notification WHERE id = #{id}")
  int deleteById(@Param("id") long id);

  @Update("""
      UPDATE notification SET read_at = #{now}
      WHERE user_id = #{userId} AND read_at IS NULL
      """)
  int markAllRead(@Param("userId") long userId, @Param("now") LocalDateTime now);

  @Lang(XMLLanguageDriver.class)
  @Update("""
      <script>
      UPDATE notification SET read_at = #{now}
      WHERE user_id = #{userId} AND read_at IS NULL
        AND id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
      </script>
      """)
  int markRead(
      @Param("userId") long userId,
      @Param("ids") List<Long> ids,
      @Param("now") LocalDateTime now);
}
