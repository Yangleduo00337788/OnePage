package com.onepage.content.dto;

import java.util.List;

/** Feed / 详情 JSON。id 一律 String，避免 JS 丢精度。 */
public final class FeedDtos {
  private FeedDtos() {}

  public record UserBrief(String userId, String pageId, String nickname, String avatarUrl) {}

  public record MediaItem(String mediaId, String url, String mimeType, String role) {}

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
      String publishedAt) {}

  public record CursorPage(List<PostBrief> items, String nextCursor, boolean hasMore) {}

  public record PostDetail(
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
      List<MediaItem> media,
      long likeCount,
      long favoriteCount,
      long commentCount,
      boolean likedByMe,
      boolean favoritedByMe,
      String publishedAt) {}

  public record PublishSpec(
      String title,
      String body,
      List<String> mediaIds,
      String postType,
      String category,
      Boolean original,
      String contentKind,
      String eventId,
      String visibility) {}

  public record CreateResult(String postId, String publishedAt) {}

  public record LikeResult(boolean liked, long likeCount) {}

  public record FavoriteResult(boolean favorited, long favoriteCount) {}

  public record CommentView(
      String commentId,
      UserBrief author,
      String content,
      String parentId,
      String rootId,
      long likeCount,
      boolean likedByMe,
      long replyCount,
      String createdAt) {}

  public record CommentPage(List<CommentView> items, long total) {}
}
