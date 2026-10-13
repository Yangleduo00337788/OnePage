package com.onepage.user.dto;

import java.time.LocalDateTime;

/** 关注/粉丝列表游标行。 */
public class FollowHit {
  private Long userId;
  private LocalDateTime at;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public LocalDateTime getAt() {
    return at;
  }

  public void setAt(LocalDateTime at) {
    this.at = at;
  }
}
