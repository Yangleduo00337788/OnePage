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
      String reasonCode,
      String detail,
      String status,
      UserSummary reporter,
      UserSummary handler,
      String handleAction,
      String handleNote,
      String createdAt,
      String handledAt) {}

  public record OpsUser(
      String userId,
      String pageId,
      String nickname,
      String avatarUrl,
      boolean frozen) {}

  public record CursorPage(List<OpsReport> items, String nextCursor, boolean hasMore) {}
}
