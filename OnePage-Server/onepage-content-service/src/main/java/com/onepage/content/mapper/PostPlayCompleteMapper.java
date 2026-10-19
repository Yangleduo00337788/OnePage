package com.onepage.content.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface PostPlayCompleteMapper {
  @Insert("""
      INSERT IGNORE INTO post_play_complete (user_id, post_id, created_at)
      VALUES (#{userId}, #{postId}, #{createdAt})
      """)
  int insertIgnore(
      @Param("userId") long userId,
      @Param("postId") long postId,
      @Param("createdAt") LocalDateTime createdAt);
}
