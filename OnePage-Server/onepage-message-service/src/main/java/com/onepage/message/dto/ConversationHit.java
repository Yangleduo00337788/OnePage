package com.onepage.message.dto;

import java.time.LocalDateTime;

public class ConversationHit {
  private Long conversationId;
  private Long peerId;
  private Long lastReadId;
  private LocalDateTime updatedAt;
  private Long lastMessageId;
  private String lastMsgType;
  private String lastContent;
  private LocalDateTime lastCreatedAt;
  private Long unreadCount;

  public Long getConversationId() { return conversationId; }
  public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
  public Long getPeerId() { return peerId; }
  public void setPeerId(Long peerId) { this.peerId = peerId; }
  public Long getLastReadId() { return lastReadId; }
  public void setLastReadId(Long lastReadId) { this.lastReadId = lastReadId; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public Long getLastMessageId() { return lastMessageId; }
  public void setLastMessageId(Long lastMessageId) { this.lastMessageId = lastMessageId; }
  public String getLastMsgType() { return lastMsgType; }
  public void setLastMsgType(String lastMsgType) { this.lastMsgType = lastMsgType; }
  public String getLastContent() { return lastContent; }
  public void setLastContent(String lastContent) { this.lastContent = lastContent; }
  public LocalDateTime getLastCreatedAt() { return lastCreatedAt; }
  public void setLastCreatedAt(LocalDateTime lastCreatedAt) { this.lastCreatedAt = lastCreatedAt; }
  public Long getUnreadCount() { return unreadCount; }
  public void setUnreadCount(Long unreadCount) { this.unreadCount = unreadCount; }
}
