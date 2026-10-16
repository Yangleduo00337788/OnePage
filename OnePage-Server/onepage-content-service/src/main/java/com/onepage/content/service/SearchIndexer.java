package com.onepage.content.service;

import com.onepage.common.tx.AfterCommit;
import com.onepage.content.client.SearchClient;
import com.onepage.content.entity.Post;
import com.onepage.content.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 事务提交后同步公开笔记到 ES。失败只打日志。 */
@Component
public class SearchIndexer {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);
  private static final String STATUS_PUBLISHED = "published";

  private final PostMapper postMapper;
  private final PostService postService;
  private final SearchClient searchClient;
  private final String internalToken;

  public SearchIndexer(
      PostMapper postMapper,
      @Lazy PostService postService,
      SearchClient searchClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.postMapper = postMapper;
    this.postService = postService;
    this.searchClient = searchClient;
    this.internalToken = internalToken;
  }

  public void sync(long postId) {
    AfterCommit.run(() -> {
      try {
        Post post = postMapper.selectById(postId);
        if (searchable(post)) {
          searchClient.upsertPost(internalToken, postService.toBrief(post));
        } else {
          searchClient.deletePost(internalToken, String.valueOf(postId));
        }
      } catch (RuntimeException ex) {
        log.warn("search index post {} failed", postId, ex);
      }
    });
  }

  static boolean searchable(Post post) {
    if (post == null || post.getDeletedAt() != null) {
      return false;
    }
    if (!STATUS_PUBLISHED.equals(post.getStatus())) {
      return false;
    }
    if (post.getVisibility() == null || post.getVisibility() != 0) {
      return false;
    }
    if (post.getPublishedAt() != null && post.getPublishedAt().isAfter(LocalDateTime.now())) {
      return false;
    }
    return true;
  }
}
