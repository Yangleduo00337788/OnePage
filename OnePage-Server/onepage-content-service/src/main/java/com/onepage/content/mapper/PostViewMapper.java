package com.onepage.content.mapper;

import com.onepage.content.dto.InteractionHit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostViewMapper {
  @Insert("""
      INSERT INTO post_view (user_id, post_id, viewed_at)
      VALUES (#{userId}, #{postId}, #{viewedAt})
      ON DUPLICATE KEY UPDATE viewed_at = #{viewedAt}
      """)
  void upsert(@Param("userId") Long userId, @Param("postId") Long postId, @Param("viewedAt") LocalDateTime viewedAt);

  @Select("SELECT COUNT(*) FROM post_view WHERE post_id = #{postId}")
  long countByPostId(@Param("postId") long postId);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT v.post_id AS postId, v.viewed_at AS at
      FROM post_view v
      INNER JOIN post p ON p.id = v.post_id
      WHERE v.user_id = #{userId}
        AND p.deleted_at IS NULL
        AND p.status = 'published'
        AND (p.visibility = 0 OR p.author_id = #{userId})
        <if test="cursorAt != null">
          AND (v.viewed_at &lt; #{cursorAt}
            OR (v.viewed_at = #{cursorAt} AND v.post_id &lt; #{cursorPostId}))
        </if>
      ORDER BY v.viewed_at DESC, v.post_id DESC
      LIMIT #{limit}
      </script>
      """)
  List<InteractionHit> pageByUser(
      @Param("userId") long userId,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorPostId") Long cursorPostId,
      @Param("limit") int limit);
}
