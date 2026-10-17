package com.onepage.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("live_chat")
public class LiveChat {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long sessionId;
  private Long userId;
  private String body;
  private String msgType;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getSessionId() { return sessionId; }
  public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getMsgType() { return msgType; }
  public void setMsgType(String msgType) { this.msgType = msgType; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
