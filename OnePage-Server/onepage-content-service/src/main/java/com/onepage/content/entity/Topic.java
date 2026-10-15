package com.onepage.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("topic")
public class Topic {
  @TableId(type = IdType.INPUT)
  private Long id;
  private String name;
  private Integer official;
  private Integer sortOrder;
  private Integer enabled;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public Integer getOfficial() { return official; }
  public void setOfficial(Integer official) { this.official = official; }
  public Integer getSortOrder() { return sortOrder; }
  public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
  public Integer getEnabled() { return enabled; }
  public void setEnabled(Integer enabled) { this.enabled = enabled; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
