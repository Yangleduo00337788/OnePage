package com.onepage.search.dto;

import java.util.List;

/** 搜索 JSON，字段对齐 Feed {@code PostBrief} 与用户摘要。 */
public final class SearchDtos {
  private SearchDtos() {}

  public record UserBrief(String userId, String pageId, String nickname, String avatarUrl) {}

  public record PostBrief(
      String postId,
      UserBrief author,
      String title,
      String body,
      String coverUrl,
      String postType,
      String category,
      boolean original,
      String contentKind,
      String eventId,
      String eventTitle,
      String visibility,
      long likeCount,
      long favoriteCount,
      long commentCount,
      long viewCount,
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}

  public record UserDoc(
      String userId, String pageId, String nickname, String avatarUrl, String bio) {}

  public record PostPage(List<PostBrief> items, String nextCursor, boolean hasMore) {}

  public record UserPage(List<UserDoc> items, String nextCursor, boolean hasMore) {}

  public record SearchResult(List<PostBrief> posts, List<UserDoc> users, String nextCursor, boolean hasMore) {}

  public record PostFilter(
      String q,
      String postType,
      String sort,
      String time,
      String scope,
      List<String> authorIds,
      List<String> seenIds,
      int from,
      int size) {}
}
