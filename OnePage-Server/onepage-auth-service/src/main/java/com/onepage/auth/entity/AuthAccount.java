package com.onepage.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 登录账号表。{@code @TableName} 映射表名；字段名默认下划线转驼峰。
 * {@code IdType.INPUT}：主键自己 {@link com.onepage.common.id.IdGenerator} 生成，不用数据库自增
 * （user 库的 user_id 必须和这里的 id 相同）。
 * status：0 正常，非 0 不可登录。
 */
@TableName("auth_account")
public class AuthAccount {
  @TableId(type = IdType.INPUT)
  private Long id;
  private String phone;
  private String email;
  private String passwordHash;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public Integer getStatus() { return status; }
  public void setStatus(Integer status) { this.status = status; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
