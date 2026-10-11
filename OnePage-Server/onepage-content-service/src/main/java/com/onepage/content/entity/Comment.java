package com.onepage.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("comment")
public class Comment {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long postId;
  private Long userId;
  private Long rootId;
  private Long parentId;
  private String content;
  private Integer likeCount;
  private Integer status;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getPostId() { return postId; }
  public void setPostId(Long postId) { this.postId = postId; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Long getRootId() { return rootId; }
  public void setRootId(Long rootId) { this.rootId = rootId; }
  public Long getParentId() { return parentId; }
  public void setParentId(Long parentId) { this.parentId = parentId; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public Integer getLikeCount() { return likeCount; }
  public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
  public Integer getStatus() { return status; }
  public void setStatus(Integer status) { this.status = status; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
