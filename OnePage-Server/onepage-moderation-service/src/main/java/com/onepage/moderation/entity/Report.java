package com.onepage.moderation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 举报工单。status：0 待处理 1 已处理。 */
@TableName("report")
public class Report {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long reporterId;
  private String targetType;
  private Long targetId;
  private String reasonCode;
  private String detail;
  private Integer status;
  private Long handlerId;
  private String handleAction;
  private String handleNote;
  private LocalDateTime handledAt;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReporterId() { return reporterId; }
  public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
  public String getTargetType() { return targetType; }
  public void setTargetType(String targetType) { this.targetType = targetType; }
  public Long getTargetId() { return targetId; }
  public void setTargetId(Long targetId) { this.targetId = targetId; }
  public String getReasonCode() { return reasonCode; }
  public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
  public String getDetail() { return detail; }
  public void setDetail(String detail) { this.detail = detail; }
  public Integer getStatus() { return status; }
  public void setStatus(Integer status) { this.status = status; }
  public Long getHandlerId() { return handlerId; }
  public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
  public String getHandleAction() { return handleAction; }
  public void setHandleAction(String handleAction) { this.handleAction = handleAction; }
  public String getHandleNote() { return handleNote; }
  public void setHandleNote(String handleNote) { this.handleNote = handleNote; }
  public LocalDateTime getHandledAt() { return handledAt; }
  public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
