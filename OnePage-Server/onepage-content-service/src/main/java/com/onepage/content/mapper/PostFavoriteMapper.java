package com.onepage.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.content.dto.InteractionHit;
import com.onepage.content.dto.ReceivedActorHit;
import com.onepage.content.entity.PostFavorite;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavorite> {
  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT f.post_id AS postId, f.created_at AS at
      FROM post_favorite f
      INNER JOIN post p ON p.id = f.post_id
      WHERE f.user_id = #{userId}
        AND p.deleted_at IS NULL
        AND p.status = 'published'
        AND (p.visibility = 0 OR p.author_id = #{userId})
        <if test="cursorAt != null">
          AND (f.created_at &lt; #{cursorAt}
            OR (f.created_at = #{cursorAt} AND f.post_id &lt; #{cursorPostId}))
        </if>
      ORDER BY f.created_at DESC, f.post_id DESC
      LIMIT #{limit}
      </script>
      """)
  List<InteractionHit> pageByUser(
      @Param("userId") long userId,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorPostId") Long cursorPostId,
      @Param("limit") int limit);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT f.user_id AS userId, f.post_id AS postId, IFNULL(p.title, '') AS title, f.created_at AS at
      FROM post_favorite f
      INNER JOIN post p ON p.id = f.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND p.status = 'published'
        <if test="cursorAt != null">
          AND (f.created_at &lt; #{cursorAt}
            OR (f.created_at = #{cursorAt} AND f.post_id &lt; #{cursorPostId})
            OR (f.created_at = #{cursorAt} AND f.post_id = #{cursorPostId} AND f.user_id &lt; #{cursorUserId}))
        </if>
      ORDER BY f.created_at DESC, f.post_id DESC, f.user_id DESC
      LIMIT #{limit}
      </script>
      """)
  List<ReceivedActorHit> pageReceived(
      @Param("authorId") long authorId,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorPostId") Long cursorPostId,
      @Param("cursorUserId") Long cursorUserId,
      @Param("limit") int limit);
}
