package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Post;
import com.onepage.content.mapper.PostMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 发现流：已发布笔记按时间倒序。关注流 P1 没有关注关系，与发现流同一套数据。
 */
@Service
public class FeedService {
  private final PostMapper postMapper;
  private final PostService postService;

  public FeedService(PostMapper postMapper, PostService postService) {
    this.postMapper = postMapper;
    this.postService = postService;
  }

  public FeedDtos.CursorPage page(String cursor, Integer limit, String category) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LambdaQueryWrapper<Post> query = Wrappers.<Post>lambdaQuery()
        .eq(Post::getStatus, "published")
        .eq(Post::getVisibility, 0)
        .isNull(Post::getDeletedAt);
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
