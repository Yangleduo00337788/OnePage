package com.onepage.content.dto;

/** 按笔记 id 聚合的条数。 */
public class CountHit {
  private Long postId;
  private Long cnt;

  public Long getPostId() {
    return postId;
  }

  public void setPostId(Long postId) {
    this.postId = postId;
  }

  public Long getCnt() {
    return cnt;
  }

  public void setCnt(Long cnt) {
    this.cnt = cnt;
  }
}
