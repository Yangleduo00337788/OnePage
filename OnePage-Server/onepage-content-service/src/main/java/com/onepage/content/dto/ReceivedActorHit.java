package com.onepage.content.dto;

import java.time.LocalDateTime;

/** 某篇笔记上的赞/藏行：互动人、帖、时间。给作者「获赞/被收藏」列表用。 */
public class ReceivedActorHit {
  private Long userId;
  private Long postId;
  private String title;
  private LocalDateTime at;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getPostId() {
    return postId;
  }

  public void setPostId(Long postId) {
    this.postId = postId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public LocalDateTime getAt() {
    return at;
  }

  public void setAt(LocalDateTime at) {
    this.at = at;
  }
}
