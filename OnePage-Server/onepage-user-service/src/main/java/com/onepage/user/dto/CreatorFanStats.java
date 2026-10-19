package com.onepage.user.dto;

import java.util.List;

/** 创作者报表用的粉丝聚合（内网）。 */
public record CreatorFanStats(
    long followerTotal,
    long followerNew,
    long followerNewPrev,
    long reciprocalCount,
    long unfollow,
    long unfollowPrev,
    Double retention7d,
    List<DayCount> trend) {
  public record DayCount(String date, long count) {}
}
