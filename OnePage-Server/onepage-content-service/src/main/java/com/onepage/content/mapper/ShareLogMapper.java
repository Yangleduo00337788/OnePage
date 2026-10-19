package com.onepage.content.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ShareLogMapper {
  @Insert("""
      INSERT INTO share_log (id, user_id, post_id, channel, created_at)
      VALUES (#{id}, #{userId}, #{postId}, #{channel}, #{createdAt})
      """)
  void insert(
      @Param("id") long id,
      @Param("userId") long userId,
      @Param("postId") long postId,
      @Param("channel") String channel,
      @Param("createdAt") LocalDateTime createdAt);
}
