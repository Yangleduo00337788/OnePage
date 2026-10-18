package com.onepage.notification.entity;

import java.time.LocalDateTime;

/** Mapper 行。不走 SELECT *。 */
public class NotificationRow {
  private Long id;
  private Long userId;
  private String type;
  private Long actorId;
  private String aggregateKey;
  private Long postId;
  private Long commentId;
  private String payload;
  private LocalDateTime readAt;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public Long getActorId() { return actorId; }
  public void setActorId(Long actorId) { this.actorId = actorId; }
  public String getAggregateKey() { return aggregateKey; }
  public void setAggregateKey(String aggregateKey) { this.aggregateKey = aggregateKey; }
  public Long getPostId() { return postId; }
  public void setPostId(Long postId) { this.postId = postId; }
  public Long getCommentId() { return commentId; }
  public void setCommentId(Long commentId) { this.commentId = commentId; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public LocalDateTime getReadAt() { return readAt; }
  public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
