package com.onepage.notification.dto;

import java.util.List;

/** 通知 JSON。id 一律 String。 */
public final class NotifyDtos {
  private NotifyDtos() {}

  public record UserBrief(
      String userId, String pageId, String nickname, String avatarUrl, boolean liveHost, boolean verified, boolean official) {}

  public record NotificationItem(
      String id,
      String type,
      UserBrief actor,
      String postId,
      String commentId,
      String preview,
      int actorCount,
      boolean read,
      String createdAt,
      String coverUrl) {}

  public record CursorPage(List<NotificationItem> items, String nextCursor, boolean hasMore) {}

  public record UnreadCount(long count, long comment, long reaction, long follow, long review) {}

  public record ReadRequest(List<String> ids, Boolean all) {}

  public record IngestRequest(
      Long recipientId,
      String type,
      Long actorId,
      String aggregateKey,
      Long postId,
      Long commentId,
      String preview) {}
}
