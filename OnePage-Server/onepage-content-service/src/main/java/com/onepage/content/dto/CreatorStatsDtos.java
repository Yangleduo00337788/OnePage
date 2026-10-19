package com.onepage.content.dto;

import java.util.List;

/** 创作者数据报表 JSON。 */
public final class CreatorStatsDtos {
  private CreatorStatsDtos() {}

  public record Metric(long value, Double delta) {}

  public record DayPoint(String date, long value) {}

  public record Mix(long image, long video, long article) {}

  public record PostRow(
      String postId,
      String title,
      String postType,
      long views,
      long likes,
      long comments,
      long favorites) {}

  public record Account(
      Metric impressions,
      Metric views,
      Metric likes,
      Metric comments,
      Metric favorites,
      Metric shares,
      Metric followerNet,
      Metric followerNew,
      Metric coverCtr,
      Metric videoCompleteRate,
      Mix mix,
      List<DayPoint> viewTrend) {}

  public record Pending(long draft, long review) {}

  public record Content(
      long avgViews,
      long avgLikes,
      long avgComments,
      long postCount,
      List<PostRow> posts) {}

  public record Fans(
      Metric followerTotal,
      Metric followerNet,
      Metric followerNew,
      Long unfollow,
      Double activeRate,
      Double reciprocalRate,
      long viewerFans,
      long engagedFans,
      Double retention7d,
      MixChange change,
      List<DayPoint> trend) {}

  public record MixChange(long followNew, Long unfollow, long net) {}

  public record Stats(
      String range, String from, String to, Account account, Content content, Fans fans, Pending pending) {}
}
