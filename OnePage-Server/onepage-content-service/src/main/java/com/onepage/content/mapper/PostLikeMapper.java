package com.onepage.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.content.dto.InteractionHit;
import com.onepage.content.entity.PostLike;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {
  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT l.post_id AS postId, l.created_at AS at
      FROM post_like l
      INNER JOIN post p ON p.id = l.post_id
      WHERE l.user_id = #{userId}
        AND p.deleted_at IS NULL
        AND p.status = 'published'
        AND (p.visibility = 0 OR p.author_id = #{userId})
        <if test="cursorAt != null">
          AND (l.created_at &lt; #{cursorAt}
            OR (l.created_at = #{cursorAt} AND l.post_id &lt; #{cursorPostId}))
        </if>
      ORDER BY l.created_at DESC, l.post_id DESC
      LIMIT #{limit}
      </script>
      """)
  List<InteractionHit> pageByUser(
      @Param("userId") long userId,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorPostId") Long cursorPostId,
      @Param("limit") int limit);
}
