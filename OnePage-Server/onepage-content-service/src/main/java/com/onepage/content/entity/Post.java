package com.onepage.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 笔记正文。status=published 才进 Feed；软删写 deleted_at。 */
@TableName("post")
public class Post {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long authorId;
  private String title;
  private String body;
  private String postType;
  private String category;
  private String status;
  private Integer visibility;
  private LocalDateTime publishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getAuthorId() { return authorId; }
  public void setAuthorId(Long authorId) { this.authorId = authorId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getPostType() { return postType; }
  public void setPostType(String postType) { this.postType = postType; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Integer getVisibility() { return visibility; }
  public void setVisibility(Integer visibility) { this.visibility = visibility; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
