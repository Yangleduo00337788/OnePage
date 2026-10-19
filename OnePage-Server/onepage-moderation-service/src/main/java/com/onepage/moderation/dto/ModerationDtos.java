package com.onepage.moderation.dto;

import com.onepage.common.dto.UserSummary;

import java.util.List;

/** 治理 JSON。id 一律 String。 */
public final class ModerationDtos {
  private ModerationDtos() {}

  public record CreateReportRequest(String targetType, String targetId, String reasonCode, String detail) {}

  public record CreateReportResult(String reportId) {}

  public record ResolveRequest(String action, String note) {}

  public record ResolveResult(String reportId, String status) {}

  public record OpsReport(
      String reportId,
      String targetType,
      String targetId,
      String targetTitle,
      String targetPageId,
      String authorId,
      boolean takenDown,
      String reasonCode,
      String detail,
      String status,
      UserSummary reporter,
      UserSummary handler,
      String handleAction,
      String handleNote,
      String createdAt,
      String handledAt) {}

  public record OpsPostView(
      String postId,
      UserSummary author,
      String title,
      String body,
      String coverUrl,
      String postType,
      String category,
      boolean original,
      String contentKind,
      String eventTitle,
      String visibility,
      List<ContentMedia> media,
      boolean takenDown,
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}

  public record ContentMedia(String mediaId, String url, String mimeType, String role) {}

  public record OpsUser(
      String userId,
      String pageId,
      String nickname,
      String avatarUrl,
      boolean frozen) {}

  public record OpsLiveView(
      String sessionId,
      String roomId,
      String title,
      String status,
      String coverUrl,
      String notice,
      UserSummary owner,
      boolean ended) {}

  public record CursorPage(List<OpsReport> items, String nextCursor, boolean hasMore) {}

  public record OpsPostRow(
      String postId,
      UserSummary author,
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
      UserSummary author,
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

  public record OpsCatalog(
      List<OpsCategory> categories,
      List<OpsCampaign> campaigns,
      List<OpsTopic> topics,
      List<OpsNotice> notices) {}

  public record OpsOverview(
      long communityUsers,
      long postsLive,
      long postsDown,
      long comments,
      long reportsPending,
      long reportsDone,
      long categories,
      long campaigns,
      long topics) {}

  public record OpsUserPage(List<OpsUser> items, String nextCursor, boolean hasMore) {}

  public record CategorySpec(String name, Integer sortOrder, Boolean enabled) {}

  public record CampaignSpec(
      String eventId, String title, String intro, String coverUrl, String status, Integer sortOrder, Boolean enabled) {}

  public record TopicSpec(String name, Boolean official, Integer sortOrder, Boolean enabled) {}

  public record NoticeSpec(String title, String body, Integer sortOrder, Boolean enabled) {}

  public record OpsImPeer(String userId, String pageId, String nickname, String avatarUrl) {}

  public record OpsConversationRow(
      String conversationId, OpsImPeer left, OpsImPeer right, String preview, String updatedAt) {}

  public record OpsConversationPage(List<OpsConversationRow> items, String nextCursor, boolean hasMore) {}

  public record OpsImMessageRow(
      String messageId,
      String conversationId,
      OpsImPeer sender,
      String msgType,
      String preview,
      String imageUrl,
      String postId,
      boolean hidden,
      String createdAt) {}

  public record OpsImMessagePage(List<OpsImMessageRow> items, String nextCursor, boolean hasMore) {}

  public record OpsMediaRow(
      String mediaId,
      UserSummary owner,
      String purpose,
      String mimeType,
      long sizeBytes,
      String status,
      String url,
      String postId,
      boolean hidden,
      String createdAt) {}

  public record OpsMediaPage(List<OpsMediaRow> items, String nextCursor, boolean hasMore) {}

  public record OpsPeerPage(List<UserSummary> items, String nextCursor, boolean hasMore) {}

  public record OpsNotifyRow(
      String id,
      String type,
      UserSummary actor,
      String postId,
      String commentId,
      String preview,
      boolean read,
      String createdAt) {}

  public record OpsNotifyPage(List<OpsNotifyRow> items, String nextCursor, boolean hasMore) {}
}
