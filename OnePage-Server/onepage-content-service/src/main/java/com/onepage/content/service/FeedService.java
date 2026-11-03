package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.content.client.UserClient;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Post;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.mapper.PostCoverShowMapper;
import com.onepage.content.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 发现流：已发布公开且到达展示时间的笔记。关注流含公开与好友可见；未登录由前端拦登录。
 */
@Service
public class FeedService {
  private static final Logger log = LoggerFactory.getLogger(FeedService.class);
  private final PostMapper postMapper;
  private final PostService postService;
  private final CoverShowRecorder coverShowRecorder;
  private final ExplorePageCache explorePageCache;
  private final UserClient userClient;
  private final String internalToken;

  public FeedService(
      PostMapper postMapper,
      PostService postService,
      CoverShowRecorder coverShowRecorder,
      ExplorePageCache explorePageCache,
      UserClient userClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.postMapper = postMapper;
    this.postService = postService;
    this.coverShowRecorder = coverShowRecorder;
    this.explorePageCache = explorePageCache;
    this.userClient = userClient;
    this.internalToken = internalToken;
  }

  public FeedDtos.CursorPage pageFollowing(Long viewerId, String cursor, Integer limit) {
    if (viewerId == null) {
      return new FeedDtos.CursorPage(List.of(), null, false);
    }
    List<Long> authorIds;
    try {
      authorIds = userClient.followeeIds(internalToken, viewerId);
    } catch (Exception ex) {
      log.warn("拉关注列表失败 userId={}", viewerId, ex);
      return new FeedDtos.CursorPage(List.of(), null, false);
    }
    if (authorIds == null || authorIds.isEmpty()) {
      return new FeedDtos.CursorPage(List.of(), null, false);
    }
    return page(viewerId, cursor, limit, null, authorIds);
  }

  public FeedDtos.CursorPage page(Long viewerId, String cursor, Integer limit, String category) {
    return page(viewerId, cursor, limit, category, null);
  }

  private FeedDtos.CursorPage page(
      Long viewerId, String cursor, Integer limit, String category, List<Long> authorIds) {
    boolean cacheable = authorIds == null && !StringUtils.hasText(cursor);
    if (cacheable) {
      FeedDtos.CursorPage page = explorePageCache.loadShared(
          category, limit, () -> loadPage(viewerId, cursor, limit, category, authorIds).page());
      coverShowRecorder.offer(showsFrom(viewerId, page));
      return page;
    }
    LoadedPage loaded = loadPage(viewerId, cursor, limit, category, authorIds);
    coverShowRecorder.offer(loaded.shows());
    return loaded.page();
  }

  private static List<PostCoverShowMapper.ShowRow> showsFrom(Long viewerId, FeedDtos.CursorPage page) {
    List<PostCoverShowMapper.ShowRow> shows = new ArrayList<>();
    if (page == null || page.items() == null) {
      return shows;
    }
    LocalDateTime now = LocalDateTime.now();
    for (FeedDtos.PostBrief item : page.items()) {
      if (item == null || !StringUtils.hasText(item.postId())) {
        continue;
      }
      try {
        shows.add(new PostCoverShowMapper.ShowRow(
            IdGenerator.nextId(), viewerId, Long.parseLong(item.postId()), now));
      } catch (NumberFormatException ignored) {
        // 缓存脏数据不挡首页
      }
    }
    return shows;
  }

  private LoadedPage loadPage(
      Long viewerId, String cursor, Integer limit, String category, List<Long> authorIds) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LambdaQueryWrapper<Post> query = Wrappers.<Post>lambdaQuery()
        .eq(Post::getStatus, "published")
        .isNull(Post::getDeletedAt)
        .le(Post::getPublishedAt, LocalDateTime.now());
    if (authorIds != null) {
      query.in(Post::getAuthorId, authorIds);
      query.in(Post::getVisibility, List.of(0, 2));
    } else {
      query.eq(Post::getVisibility, 0);
    }
    if (StringUtils.hasText(category)) {
      query.eq(Post::getCategory, category.trim());
    }
    applyCursor(query, cursor);
    query.orderByDesc(Post::getPublishedAt)
        .orderByDesc(Post::getId)
        .last("LIMIT " + (size + 1));
    List<Post> rows = postMapper.selectList(query);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    List<FeedDtos.PostBrief> items = postService.toBriefs(rows);
    List<PostCoverShowMapper.ShowRow> shows = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (Post post : rows) {
      shows.add(new PostCoverShowMapper.ShowRow(IdGenerator.nextId(), viewerId, post.getId(), now));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      Post last = rows.getLast();
      next = PostService.epochMilli(last.getPublishedAt()) + "_" + last.getId();
    }
    return new LoadedPage(new FeedDtos.CursorPage(items, next, hasMore), shows);
  }

  /** 卡片返回前把曝光丢进内存队列，定时批量写 Outbox。缓存命中也记账。 */
  private record LoadedPage(FeedDtos.CursorPage page, List<PostCoverShowMapper.ShowRow> shows) {}

  private void applyCursor(LambdaQueryWrapper<Post> query, String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return;
    }
    String[] parts = cursor.split("_", 2);
    if (parts.length != 2) {
      throw BusinessException.validation("cursor 不合法");
    }
    long millis;
    long id;
    try {
      millis = Long.parseLong(parts[0]);
      id = Long.parseLong(parts[1]);
    } catch (NumberFormatException ex) {
      throw BusinessException.validation("cursor 不合法");
    }
    LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    query.and(w -> w.lt(Post::getPublishedAt, at)
        .or(inner -> inner.eq(Post::getPublishedAt, at).lt(Post::getId, id)));
  }
}
