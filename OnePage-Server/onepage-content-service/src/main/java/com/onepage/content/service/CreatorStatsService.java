package com.onepage.content.service;

import com.onepage.common.api.BusinessException;
import com.onepage.content.client.UserClient;
import com.onepage.content.dto.CreatorStatsDtos;
import com.onepage.content.mapper.CreatorStatsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CreatorStatsService {
  private static final Logger log = LoggerFactory.getLogger(CreatorStatsService.class);
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private final CreatorStatsMapper statsMapper;
  private final UserClient userClient;
  private final String internalToken;

  public CreatorStatsService(
      CreatorStatsMapper statsMapper,
      UserClient userClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.statsMapper = statsMapper;
    this.userClient = userClient;
    this.internalToken = internalToken;
  }

  public CreatorStatsDtos.Stats load(long authorId, String rangeRaw) {
    Window win = windowOf(rangeRaw);
    long views = statsMapper.countViews(authorId, win.fromAt, win.toAt);
    long viewsPrev = statsMapper.countViews(authorId, win.prevFrom, win.prevTo);
    long shows = statsMapper.countCoverShows(authorId, win.fromAt, win.toAt);
    long showsPrev = statsMapper.countCoverShows(authorId, win.prevFrom, win.prevTo);
    long likes = statsMapper.countLikes(authorId, win.fromAt, win.toAt);
    long likesPrev = statsMapper.countLikes(authorId, win.prevFrom, win.prevTo);
    long comments = statsMapper.countComments(authorId, win.fromAt, win.toAt);
    long commentsPrev = statsMapper.countComments(authorId, win.prevFrom, win.prevTo);
    long favorites = statsMapper.countFavorites(authorId, win.fromAt, win.toAt);
    long favoritesPrev = statsMapper.countFavorites(authorId, win.prevFrom, win.prevTo);
    long shares = statsMapper.countShares(authorId, win.fromAt, win.toAt);
    long sharesPrev = statsMapper.countShares(authorId, win.prevFrom, win.prevTo);
    long videoViews = statsMapper.countVideoViews(authorId, win.fromAt, win.toAt);
    long videoViewsPrev = statsMapper.countVideoViews(authorId, win.prevFrom, win.prevTo);
    long completes = statsMapper.countPlayCompletes(authorId, win.fromAt, win.toAt);
    long completesPrev = statsMapper.countPlayCompletes(authorId, win.prevFrom, win.prevTo);
    long draft = statsMapper.countByStatus(authorId, "draft");
    long review = statsMapper.countByStatus(authorId, "pending");

    MixCounts mix = mixOf(statsMapper.mixByPostType(authorId, win.fromAt, win.toAt));
    List<CreatorStatsDtos.DayPoint> viewTrend =
        fillDays(win.dates, statsMapper.viewsByDay(authorId, win.fromAt, win.toAt));

    UserClient.FanStats fansRaw = loadFans(authorId, win);
    long followerNew = fansRaw == null ? 0L : fansRaw.followerNew();
    long followerNewPrev = fansRaw == null ? 0L : fansRaw.followerNewPrev();
    long followerTotal = fansRaw == null ? 0L : fansRaw.followerTotal();
    long reciprocal = fansRaw == null ? 0L : fansRaw.reciprocalCount();
    long unfollow = fansRaw == null ? 0L : fansRaw.unfollow();
    long unfollowPrev = fansRaw == null ? 0L : fansRaw.unfollowPrev();
    long followerNet = followerNew - unfollow;
    long followerNetPrev = followerNewPrev - unfollowPrev;
    Double retention7d = fansRaw == null ? null : fansRaw.retention7d();

    List<Long> viewerIds = nvl(statsMapper.listViewerIds(authorId, win.fromAt, win.toAt));
    List<Long> engagedIds = nvl(statsMapper.listEngagedIds(authorId, win.fromAt, win.toAt));
    long viewerFans = countInFollowers(authorId, viewerIds);
    long engagedFans = countInFollowers(authorId, engagedIds);

    List<CreatorStatsMapper.PostHit> hits = nvl(statsMapper.listPosts(authorId, win.fromAt, win.toAt));
    List<CreatorStatsDtos.PostRow> posts = new ArrayList<>();
    long viewSum = 0L;
    long likeSum = 0L;
    long commentSum = 0L;
    for (CreatorStatsMapper.PostHit hit : hits) {
      viewSum += hit.views();
      likeSum += hit.likes();
      commentSum += hit.comments();
      posts.add(new CreatorStatsDtos.PostRow(
          String.valueOf(hit.postId()),
          hit.title() == null ? "" : hit.title(),
          hit.postType() == null ? "image" : hit.postType(),
          hit.views(),
          hit.likes(),
          hit.comments(),
          hit.favorites()));
    }
    int postCount = posts.size();
    long avgViews = postCount == 0 ? 0L : viewSum / postCount;
    long avgLikes = postCount == 0 ? 0L : likeSum / postCount;
    long avgComments = postCount == 0 ? 0L : commentSum / postCount;

    List<CreatorStatsDtos.DayPoint> fanTrend = fillFanDays(win.dates, fansRaw);

    Double activeRate = followerTotal <= 0L ? null : (double) engagedFans / (double) followerTotal;
    Double reciprocalRate = followerTotal <= 0L ? null : (double) reciprocal / (double) followerTotal;

    CreatorStatsDtos.Account account = new CreatorStatsDtos.Account(
        metric(shows, showsPrev),
        metric(views, viewsPrev),
        metric(likes, likesPrev),
        metric(comments, commentsPrev),
        metric(favorites, favoritesPrev),
        metric(shares, sharesPrev),
        metric(followerNet, followerNetPrev),
        metric(followerNew, followerNewPrev),
        metric(percent(views, shows), percent(viewsPrev, showsPrev)),
        metric(percent(completes, videoViews), percent(completesPrev, videoViewsPrev)),
        new CreatorStatsDtos.Mix(mix.image, mix.video, mix.article),
        viewTrend);

    CreatorStatsDtos.Content content = new CreatorStatsDtos.Content(
        avgViews, avgLikes, avgComments, postCount, posts);

    CreatorStatsDtos.Fans fans = new CreatorStatsDtos.Fans(
        new CreatorStatsDtos.Metric(followerTotal, null),
        metric(followerNet, followerNetPrev),
        metric(followerNew, followerNewPrev),
        unfollow,
        activeRate,
        reciprocalRate,
        viewerFans,
        engagedFans,
        retention7d,
        new CreatorStatsDtos.MixChange(followerNew, unfollow, followerNet),
        fanTrend);

    return new CreatorStatsDtos.Stats(
        win.range,
        TS.format(win.fromAt),
        TS.format(win.toAt),
        account,
        content,
        fans,
        new CreatorStatsDtos.Pending(draft, review));
  }

  private UserClient.FanStats loadFans(long authorId, Window win) {
    try {
      return userClient.fanStats(
          internalToken,
          authorId,
          TS.format(win.fromAt),
          TS.format(win.toAt),
          TS.format(win.prevFrom),
          TS.format(win.prevTo));
    } catch (RuntimeException ex) {
      log.warn("creator fan stats unavailable: {}", ex.toString());
      return null;
    }
  }

  private long countInFollowers(long authorId, List<Long> ids) {
    if (ids.isEmpty()) {
      return 0L;
    }
    try {
      UserClient.CountView view = userClient.countInFollowers(internalToken, authorId, ids);
      return view == null ? 0L : view.count();
    } catch (RuntimeException ex) {
      log.warn("count-in-followers unavailable: {}", ex.toString());
      return 0L;
    }
  }

  private static Window windowOf(String rangeRaw) {
    String range = rangeRaw == null ? "7" : rangeRaw.trim().toLowerCase(Locale.ROOT);
    int days;
    if ("1".equals(range)) {
      days = 1;
    } else if ("30".equals(range)) {
      days = 30;
    } else if ("7".equals(range)) {
      days = 7;
    } else {
      throw BusinessException.validation("range 只支持 1、7、30");
    }
    LocalDate today = LocalDate.now(ZONE);
    LocalDate start;
    LocalDate endExclusive;
    if (days == 1) {
      start = today.minusDays(1);
      endExclusive = today;
    } else {
      start = today.minusDays(days - 1L);
      endExclusive = today.plusDays(1);
    }
    LocalDateTime fromAt = start.atStartOfDay();
    LocalDateTime toAt = endExclusive.atStartOfDay();
    LocalDateTime prevTo = fromAt;
    LocalDateTime prevFrom = fromAt.minusDays(days);
    List<LocalDate> dates = new ArrayList<>();
    for (LocalDate d = start; d.isBefore(endExclusive); d = d.plusDays(1)) {
      dates.add(d);
    }
    return new Window(range, fromAt, toAt, prevFrom, prevTo, dates);
  }

  private static CreatorStatsDtos.Metric metric(long current, long previous) {
    return new CreatorStatsDtos.Metric(current, delta(current, previous));
  }

  private static long percent(long num, long den) {
    if (den <= 0L) {
      return 0L;
    }
    return Math.round(num * 100.0d / den);
  }

  private static Double delta(long current, long previous) {
    if (previous == 0L) {
      return current == 0L ? null : 1.0d;
    }
    return (current - previous) / (double) previous;
  }

  private static MixCounts mixOf(List<CreatorStatsMapper.TypeCount> rows) {
    long image = 0L;
    long video = 0L;
    long article = 0L;
    if (rows == null) {
      return new MixCounts(0L, 0L, 0L);
    }
    for (CreatorStatsMapper.TypeCount row : rows) {
      if (row == null || row.postType() == null) {
        continue;
      }
      switch (row.postType()) {
        case "video" -> video += row.cnt();
        case "article" -> article += row.cnt();
        default -> image += row.cnt();
      }
    }
    return new MixCounts(image, video, article);
  }

  private static List<CreatorStatsDtos.DayPoint> fillDays(
      List<LocalDate> dates, List<CreatorStatsMapper.DayCount> rows) {
    Map<String, Long> map = new HashMap<>();
    if (rows != null) {
      for (CreatorStatsMapper.DayCount row : rows) {
        if (row != null && row.statDay() != null) {
          map.put(row.statDay(), row.cnt());
        }
      }
    }
    List<CreatorStatsDtos.DayPoint> out = new ArrayList<>();
    for (LocalDate date : dates) {
      String key = DAY.format(date);
      out.add(new CreatorStatsDtos.DayPoint(key, map.getOrDefault(key, 0L)));
    }
    return out;
  }

  private static List<CreatorStatsDtos.DayPoint> fillFanDays(List<LocalDate> dates, UserClient.FanStats fans) {
    Map<String, Long> map = new HashMap<>();
    if (fans != null && fans.trend() != null) {
      for (UserClient.DayCount row : fans.trend()) {
        if (row != null && row.date() != null) {
          map.put(row.date(), row.count());
        }
      }
    }
    List<CreatorStatsDtos.DayPoint> out = new ArrayList<>();
    for (LocalDate date : dates) {
      String key = DAY.format(date);
      out.add(new CreatorStatsDtos.DayPoint(key, map.getOrDefault(key, 0L)));
    }
    return out;
  }

  private static <T> List<T> nvl(List<T> list) {
    return list == null ? List.of() : list;
  }

  private record Window(
      String range,
      LocalDateTime fromAt,
      LocalDateTime toAt,
      LocalDateTime prevFrom,
      LocalDateTime prevTo,
      List<LocalDate> dates) {}

  private record MixCounts(long image, long video, long article) {}
}
