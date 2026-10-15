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
      String publishedAt) {}

  public record ContentMedia(String mediaId, String url, String mimeType, String role) {}

  public record OpsUser(
      String userId,
      String pageId,
      String nickname,
      String avatarUrl,
      boolean frozen) {}

  public record CursorPage(List<OpsReport> items, String nextCursor, boolean hasMore) {}

  public record OpsPostRow(
      String postId,
      UserSummary author,
      String title,
      String postType,
      String category,
      boolean takenDown,
      String publishedAt) {}

  public record OpsPostPage(List<OpsPostRow> items, String nextCursor, boolean hasMore) {}

  public record OpsCommentRow(
      String commentId,
      String postId,
      UserSummary author,
      String content,
      boolean hidden,
      String createdAt) {}

  public record OpsCommentPage(List<OpsCommentRow> items, String nextCursor, boolean hasMore) {}

  public record OpsCampaign(String eventId, String title) {}

  public record OpsCatalog(List<String> categories, List<OpsCampaign> campaigns) {}
}
