package com.onepage.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户资料。主键就是 auth 的账号 id（不是自增），所以跨库能对上同一个人。
 * 头像封面存 objectKey，不存完整 URL，换 MinIO 域名时不用改库。
 */
@TableName("user_profile")
public class UserProfile {
  @TableId(type = IdType.INPUT)
  private Long userId;
  private String pageId;
  private String nickname;
  private String avatarObjectKey;
  private String coverObjectKey;
  private String bio;
  private Integer gender;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getPageId() { return pageId; }
  public void setPageId(String pageId) { this.pageId = pageId; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
  public String getAvatarObjectKey() { return avatarObjectKey; }
  public void setAvatarObjectKey(String avatarObjectKey) { this.avatarObjectKey = avatarObjectKey; }
  public String getCoverObjectKey() { return coverObjectKey; }
  public void setCoverObjectKey(String coverObjectKey) { this.coverObjectKey = coverObjectKey; }
  public String getBio() { return bio; }
  public void setBio(String bio) { this.bio = bio; }
  public Integer getGender() { return gender; }
  public void setGender(Integer gender) { this.gender = gender; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
