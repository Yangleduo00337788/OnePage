package com.onepage.message.dto;

import java.time.LocalDateTime;

public class OpsConversationHit {
  private Long conversationId;
  private Long leftId;
  private Long rightId;
  private LocalDateTime updatedAt;
  private Long lastMessageId;
  private String lastMsgType;
  private String lastContent;

  public Long getConversationId() { return conversationId; }
  public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
  public Long getLeftId() { return leftId; }
  public void setLeftId(Long leftId) { this.leftId = leftId; }
  public Long getRightId() { return rightId; }
  public void setRightId(Long rightId) { this.rightId = rightId; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public Long getLastMessageId() { return lastMessageId; }
  public void setLastMessageId(Long lastMessageId) { this.lastMessageId = lastMessageId; }
  public String getLastMsgType() { return lastMsgType; }
  public void setLastMsgType(String lastMsgType) { this.lastMsgType = lastMsgType; }
  public String getLastContent() { return lastContent; }
  public void setLastContent(String lastContent) { this.lastContent = lastContent; }
}
