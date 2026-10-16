package com.onepage.search.service;

import com.onepage.search.client.ContentClient;
import com.onepage.search.client.UserClient;
import com.onepage.search.dto.SearchDtos;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 对外搜索与内部索引写入。空关键词不查，避免把全库倒出来。 */
@Service
public class SearchService {
  private static final Logger log = LoggerFactory.getLogger(SearchService.class);
  private final SearchIndex searchIndex;
  private final UserClient userClient;
  private final ContentClient contentClient;
  private final String internalToken;

  public SearchService(
      SearchIndex searchIndex,
      UserClient userClient,
      ContentClient contentClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.searchIndex = searchIndex;
    this.userClient = userClient;
    this.contentClient = contentClient;
    this.internalToken = internalToken;
  }

  @PostConstruct
  public void init() {
    searchIndex.ensureIndices();
  }

  public SearchDtos.SearchResult search(
      String q,
      String type,
      String cursor,
      Integer limit,
      String postType,
      String sort,
      String time,
      String scope,
      Long userId) {
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
      SearchDtos.PostFilter filter = postFilter(keyword, postType, sort, time, scope, userId, from, size + 1);
      if (emptyScope(filter)) {
        return new SearchDtos.SearchResult(List.of(), List.of(), null, false);
      }
      List<SearchDtos.PostBrief> page = searchIndex.searchPosts(filter);
      hasMore = page.size() > size;
      posts = hasMore ? page.subList(0, size) : page;
    } else if ("topic".equals(kind) || "live".equals(kind)) {
      return new SearchDtos.SearchResult(List.of(), List.of(), null, false);
    } else {
      SearchDtos.PostFilter filter = postFilter(keyword, postType, sort, time, scope, userId, 0, size);
      if (!emptyScope(filter)) {
        posts = searchIndex.searchPosts(filter);
      }
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

  private SearchDtos.PostFilter postFilter(
      String q,
      String postType,
      String sort,
      String time,
      String scope,
      Long userId,
      int from,
      int size) {
    String scopeKey = scope == null ? "" : scope.trim().toLowerCase();
    List<String> authors = List.of();
    List<String> seen = List.of();
    if (userId != null && "following".equals(scopeKey)) {
      authors = followeeIds(userId);
    }
    if (userId != null && ("seen".equals(scopeKey) || "unseen".equals(scopeKey))) {
      seen = historyIds(userId);
    }
    if (userId == null && ("following".equals(scopeKey) || "seen".equals(scopeKey) || "unseen".equals(scopeKey))) {
      scopeKey = "login";
    }
    return new SearchDtos.PostFilter(q, postType, sort, time, scopeKey, authors, seen, from, size);
  }

  private static boolean emptyScope(SearchDtos.PostFilter filter) {
    if ("login".equals(filter.scope())) {
      return true;
    }
    if ("following".equals(filter.scope()) && (filter.authorIds() == null || filter.authorIds().isEmpty())) {
      return true;
    }
    return "seen".equals(filter.scope()) && (filter.seenIds() == null || filter.seenIds().isEmpty());
  }

  private List<String> followeeIds(long userId) {
    try {
      List<Long> ids = userClient.followeeIds(internalToken, userId);
      List<String> out = new ArrayList<>();
      if (ids == null) {
        return out;
      }
      for (Long id : ids) {
        if (id != null) {
          out.add(String.valueOf(id));
        }
      }
      return out;
    } catch (RuntimeException ex) {
      log.warn("load followee ids failed", ex);
      return List.of();
    }
  }

  private List<String> historyIds(long userId) {
    try {
      List<String> ids = contentClient.historyIds(internalToken, userId);
      return ids == null ? List.of() : ids;
    } catch (RuntimeException ex) {
      log.warn("load history ids failed", ex);
      return List.of();
    }
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
