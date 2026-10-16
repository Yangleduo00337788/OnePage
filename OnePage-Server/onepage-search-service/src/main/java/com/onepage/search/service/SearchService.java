package com.onepage.search.service;

import com.onepage.search.dto.SearchDtos;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 对外搜索与内部索引写入。空关键词不查，避免把全库倒出来。 */
@Service
public class SearchService {
  private final SearchIndex searchIndex;

  public SearchService(SearchIndex searchIndex) {
    this.searchIndex = searchIndex;
  }

  @PostConstruct
  public void init() {
    searchIndex.ensureIndices();
  }

  public SearchDtos.SearchResult search(String q, String type, String cursor, Integer limit, String postType) {
    String keyword = q == null ? "" : q.trim();
    if (!StringUtils.hasText(keyword)) {
      return new SearchDtos.SearchResult(List.of(), List.of(), null, false);
    }
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    int from = parseFrom(cursor);
    String kind = type == null || type.isBlank() ? "all" : type.trim().toLowerCase();
    List<SearchDtos.PostBrief> posts = List.of();
    List<SearchDtos.UserDoc> users = List.of();
    boolean hasMore = false;
    if ("user".equals(kind)) {
      List<SearchDtos.UserDoc> page = searchIndex.searchUsers(keyword, from, size + 1);
      hasMore = page.size() > size;
      users = hasMore ? page.subList(0, size) : page;
    } else if ("post".equals(kind)) {
      List<SearchDtos.PostBrief> page = searchIndex.searchPosts(keyword, postType, from, size + 1);
      hasMore = page.size() > size;
      posts = hasMore ? page.subList(0, size) : page;
    } else {
      posts = searchIndex.searchPosts(keyword, postType, 0, size);
      users = searchIndex.searchUsers(keyword, 0, size);
    }
    String next = hasMore ? String.valueOf(from + size) : null;
    return new SearchDtos.SearchResult(posts, users, next, hasMore);
  }

  public void upsertPost(SearchDtos.PostBrief doc) {
    searchIndex.ensureIndices();
    searchIndex.upsertPost(doc);
  }

  public void deletePost(String postId) {
    searchIndex.deletePost(postId);
  }

  public void upsertUser(SearchDtos.UserDoc doc) {
    searchIndex.ensureIndices();
    searchIndex.upsertUser(doc);
  }

  private static int parseFrom(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return 0;
    }
    try {
      return Math.max(Integer.parseInt(cursor.trim()), 0);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }
}
