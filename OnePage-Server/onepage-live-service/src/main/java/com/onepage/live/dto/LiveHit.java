package com.onepage.live.dto;

import java.time.LocalDateTime;

/** 正在直播的房间+场次拼行。 */
public class LiveHit {
  private Long sessionId;
  private Long roomId;
  private Long ownerId;
  private String title;
  private String category;
  private String coverObjectKey;
  private String notice;
  private String streamKey;
  private String status;
  private LocalDateTime startedAt;

  public Long getSessionId() { return sessionId; }
  public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
  public Long getRoomId() { return roomId; }
  public void setRoomId(Long roomId) { this.roomId = roomId; }
  public Long getOwnerId() { return ownerId; }
  public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getCoverObjectKey() { return coverObjectKey; }
  public void setCoverObjectKey(String coverObjectKey) { this.coverObjectKey = coverObjectKey; }
  public String getNotice() { return notice; }
  public void setNotice(String notice) { this.notice = notice; }
  public String getStreamKey() { return streamKey; }
  public void setStreamKey(String streamKey) { this.streamKey = streamKey; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDateTime getStartedAt() { return startedAt; }
  public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
}
