package com.onepage.notification.dto;

/** 未读按 type 聚合。cnt 对 MyBatis 列名。 */
public class TypeCount {
  private String type;
  private long cnt;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public long getCnt() {
    return cnt;
  }

  public void setCnt(long cnt) {
    this.cnt = cnt;
  }
}
