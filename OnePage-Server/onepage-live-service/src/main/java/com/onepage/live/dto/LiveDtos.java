package com.onepage.live.dto;

import com.onepage.common.dto.UserSummary;

import java.util.List;

/** 直播 JSON。id 用 String，避免 JS 丢精度。 */
public final class LiveDtos {
  private LiveDtos() {}

  public record RoomSpec(String title, String category, String coverMediaId) {}

  public record RoomCreated(String roomId) {}

  public record LiveStart(
      String sessionId,
      String roomId,
      String title,
      String coverUrl,
      String pushUrl,
      String playUrlHls,
      String streamKey) {}

  public record LiveItem(
      String sessionId,
      String roomId,
      String title,
      String category,
      String status,
      String coverUrl,
      String playUrlHls,
      long viewerCount,
      UserSummary owner,
      String startedAt) {}

  public record LivePage(List<LiveItem> items) {}

  public record ChatSpec(String body) {}

  public record ChatLine(
      String messageId, String kind, String body, String createdAt, UserSummary author) {}

  public record ChatPage(List<ChatLine> items) {}

  public record HeartbeatSpec(String presenceId) {}

  public record PresenceTick(String status, long viewerCount, ChatLine join) {}
}
