package com.onepage.content.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface PostViewMapper {
  @Insert("""
      INSERT INTO post_view (user_id, post_id, viewed_at)
      VALUES (#{userId}, #{postId}, #{viewedAt})
      ON DUPLICATE KEY UPDATE viewed_at = #{viewedAt}
      """)
  void upsert(@Param("userId") Long userId, @Param("postId") Long postId, @Param("viewedAt") LocalDateTime viewedAt);
}
