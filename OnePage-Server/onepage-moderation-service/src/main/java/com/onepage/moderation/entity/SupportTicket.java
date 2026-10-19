package com.onepage.moderation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 帮助反馈与入驻/认证申请。status：0 待处理 1 已处理。 */
@TableName("support_ticket")
public class SupportTicket {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long userId;
  private String kind;
  private String title;
  private String contact;
  private String body;
  private String mediaUrls;
  private Integer status;
  private Long handlerId;
  private String handleNote;
  private LocalDateTime handledAt;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getContact() { return contact; }
  public void setContact(String contact) { this.contact = contact; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getMediaUrls() { return mediaUrls; }
  public void setMediaUrls(String mediaUrls) { this.mediaUrls = mediaUrls; }
  public Integer getStatus() { return status; }
  public void setStatus(Integer status) { this.status = status; }
  public Long getHandlerId() { return handlerId; }
  public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
  public String getHandleNote() { return handleNote; }
  public void setHandleNote(String handleNote) { this.handleNote = handleNote; }
  public LocalDateTime getHandledAt() { return handledAt; }
  public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
