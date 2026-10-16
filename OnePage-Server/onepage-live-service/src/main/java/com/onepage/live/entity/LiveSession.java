package com.onepage.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("live_session")
public class LiveSession {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long roomId;
  private String streamKey;
  private String status;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getRoomId() { return roomId; }
  public void setRoomId(Long roomId) { this.roomId = roomId; }
  public String getStreamKey() { return streamKey; }
  public void setStreamKey(String streamKey) { this.streamKey = streamKey; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDateTime getStartedAt() { return startedAt; }
  public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
  public LocalDateTime getEndedAt() { return endedAt; }
  public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
