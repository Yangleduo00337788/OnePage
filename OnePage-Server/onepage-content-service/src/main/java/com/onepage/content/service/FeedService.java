package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.content.client.UserClient;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Post;
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
 * 发现流：已发布公开笔记按时间倒序。关注流只出已关注作者；未登录或未关注任何人为空页。
 */
@Service
public class FeedService {
  private static final Logger log = LoggerFactory.getLogger(FeedService.class);
  private final PostMapper postMapper;
  private final PostService postService;
  private final UserClient userClient;
  private final String internalToken;

  public FeedService(
      PostMapper postMapper,
      PostService postService,
      UserClient userClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.postMapper = postMapper;
    this.postService = postService;
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
    return page(cursor, limit, null, authorIds);
  }

  public FeedDtos.CursorPage page(String cursor, Integer limit, String category) {
    return page(cursor, limit, category, null);
  }

  private FeedDtos.CursorPage page(String cursor, Integer limit, String category, List<Long> authorIds) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LambdaQueryWrapper<Post> query = Wrappers.<Post>lambdaQuery()
        .eq(Post::getStatus, "published")
        .eq(Post::getVisibility, 0)
        .isNull(Post::getDeletedAt);
    if (authorIds != null) {
      query.in(Post::getAuthorId, authorIds);
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
    List<FeedDtos.PostBrief> items = new ArrayList<>();
    for (Post post : rows) {
      items.add(postService.toBrief(post));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      Post last = rows.getLast();
      next = PostService.epochMilli(last.getPublishedAt()) + "_" + last.getId();
    }
    return new FeedDtos.CursorPage(items, next, hasMore);
  }

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
