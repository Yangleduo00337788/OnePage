package com.onepage.content.dto;

import java.time.LocalDateTime;

/** 赞/藏/浏览列表的一页游标行：按互动时间倒序，再还原成 PostBrief。 */
public class InteractionHit {
  private Long postId;
  private LocalDateTime at;

  public Long getPostId() {
    return postId;
  }

  public void setPostId(Long postId) {
    this.postId = postId;
  }

  public LocalDateTime getAt() {
    return at;
  }

  public void setAt(LocalDateTime at) {
    this.at = at;
  }
}
