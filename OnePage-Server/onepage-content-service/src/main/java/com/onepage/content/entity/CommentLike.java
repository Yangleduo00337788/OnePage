package com.onepage.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("comment_like")
public class CommentLike {
  @TableId(value = "user_id", type = IdType.INPUT)
  private Long userId;
  private Long commentId;
  private LocalDateTime createdAt;

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Long getCommentId() { return commentId; }
  public void setCommentId(Long commentId) { this.commentId = commentId; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
