package com.onepage.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 第三方账号绑定。同一 user 可绑多个 provider；同一 provider+providerUserId 只能对应一个 user。
 */
@TableName("auth_oauth_binding")
public class AuthOauthBinding {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long userId;
  private String provider;
  private String providerUserId;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getProviderUserId() { return providerUserId; }
  public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
