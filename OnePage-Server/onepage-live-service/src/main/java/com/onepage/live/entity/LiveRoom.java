package com.onepage.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("live_room")
public class LiveRoom {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long ownerId;
  private String title;
  private String category;
  private String coverObjectKey;
  private String notice;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getOwnerId() { return ownerId; }
  public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getCoverObjectKey() { return coverObjectKey; }
  public void setCoverObjectKey(String coverObjectKey) { this.coverObjectKey = coverObjectKey; }
  public String getNotice() { return notice; }
  public void setNotice(String notice) { this.notice = notice; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
