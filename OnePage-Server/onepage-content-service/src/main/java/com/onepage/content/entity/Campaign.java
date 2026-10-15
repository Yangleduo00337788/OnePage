package com.onepage.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("campaign")
public class Campaign {
  @TableId(type = IdType.INPUT)
  private String id;
  private String title;
  private String intro;
  private String coverUrl;
  private String status;
  private Integer sortOrder;
  private Integer enabled;
  private LocalDateTime createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getIntro() { return intro; }
  public void setIntro(String intro) { this.intro = intro; }
  public String getCoverUrl() { return coverUrl; }
  public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Integer getSortOrder() { return sortOrder; }
  public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
  public Integer getEnabled() { return enabled; }
  public void setEnabled(Integer enabled) { this.enabled = enabled; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
