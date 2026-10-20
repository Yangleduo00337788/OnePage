package com.onepage.content.dto;

import java.util.List;

/** Feed / 详情 JSON。id 一律 String，避免 JS 丢精度。 */
public final class FeedDtos {
  private FeedDtos() {}

  public record UserBrief(
      String userId, String pageId, String nickname, String avatarUrl, boolean liveHost, boolean verified, boolean official) {}

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
      long viewCount,
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}

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
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}

  public record PublishSpec(
      String title,
      String body,
      List<String> mediaIds,
      String postType,
      String category,
      Boolean original,
      String contentKind,
      String eventId,
      String visibility,
      Boolean draft,
      String scheduledAt) {}

  public record CreateResult(String postId, String publishedAt, String reviewStatus) {}

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

  public record ReceivedHit(String userId, String postId, String postTitle, String createdAt) {}

  public record ReceivedPage(List<ReceivedHit> items, String nextCursor, boolean hasMore) {}

  /** 治理工单用的笔记摘要。已下架也返回标题。 */
  public record OpsPostBrief(String postId, String title, String authorId, boolean takenDown, String coverUrl) {}

  /** 运营预览：已下架也能看正文和媒体。 */
  public record OpsPostView(
      String postId,
      UserBrief author,
      String title,
      String body,
      String coverUrl,
      String postType,
      String category,
      boolean original,
      String contentKind,
      String eventTitle,
      String visibility,
      List<MediaItem> media,
      boolean takenDown,
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}

  public record OpsPostRow(
      String postId,
      UserBrief author,
      String title,
      String postType,
      String category,
      boolean takenDown,
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}

  public record OpsPostPage(List<OpsPostRow> items, String nextCursor, boolean hasMore) {}

  public record OpsCommentRow(
      String commentId,
      String postId,
      UserBrief author,
      String content,
      boolean hidden,
      String createdAt) {}

  public record OpsCommentPage(List<OpsCommentRow> items, String nextCursor, boolean hasMore) {}

  public record OpsCampaign(
      String eventId,
      String title,
      String intro,
      String coverUrl,
      String status,
      int sortOrder,
      boolean enabled) {}

  public record OpsCategory(String categoryId, String name, int sortOrder, boolean enabled) {}

  public record OpsTopic(
      String topicId, String name, boolean official, int sortOrder, boolean enabled, long postCount) {}

  public record OpsNotice(String noticeId, String title, String body, int sortOrder, boolean enabled) {}

  public record TopicIndexDoc(String topicId, String name, boolean official, int sortOrder) {}

  public record TopicIndexPage(List<TopicIndexDoc> items, String nextCursor, boolean hasMore) {}

  public record OpsCatalog(
      List<OpsCategory> categories,
      List<OpsCampaign> campaigns,
      List<OpsTopic> topics,
      List<OpsNotice> notices) {}

  public record PublicCatalog(
      List<OpsCategory> categories,
      List<OpsCampaign> campaigns,
      List<OpsTopic> topics,
      List<OpsNotice> notices) {}

  public record ContentStats(
      long postsLive,
      long postsDown,
      long postsPending,
      long postsRejected,
      long comments,
      long postsToday,
      long commentsToday) {}

  public record CategorySpec(String name, Integer sortOrder, Boolean enabled) {}

  public record CampaignSpec(
      String eventId, String title, String intro, String coverUrl, String status, Integer sortOrder, Boolean enabled) {}

  public record TopicSpec(String name, Boolean official, Integer sortOrder, Boolean enabled) {}

  public record NoticeSpec(String title, String body, Integer sortOrder, Boolean enabled) {}

  public record OpsFaq(
      String faqId,
      String groupId,
      String groupTitle,
      String question,
      String answer,
      int sortOrder,
      boolean enabled) {}

  public record FaqSpec(String groupId, String question, String answer, Integer sortOrder, Boolean enabled) {}

  public record OpsMediaRow(
      String mediaId,
      UserBrief owner,
      String purpose,
      String mimeType,
      long sizeBytes,
      String status,
      String url,
      String postId,
      boolean hidden,
      String createdAt) {}

  public record OpsMediaPage(List<OpsMediaRow> items, String nextCursor, boolean hasMore) {}
}
