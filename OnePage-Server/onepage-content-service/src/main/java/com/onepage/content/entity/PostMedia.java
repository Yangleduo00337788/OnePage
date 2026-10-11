package com.onepage.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 笔记挂的媒体。role：cover / gallery / video。 */
@TableName("post_media")
public class PostMedia {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long postId;
  private Long mediaId;
  private Integer sortOrder;
  private String role;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getPostId() { return postId; }
  public void setPostId(Long postId) { this.postId = postId; }
  public Long getMediaId() { return mediaId; }
  public void setMediaId(Long mediaId) { this.mediaId = mediaId; }
  public Integer getSortOrder() { return sortOrder; }
  public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
}
