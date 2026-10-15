package com.onepage.im.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 私信 JSON。id 一律 String。 */
public final class ImDtos {
  private ImDtos() {}

  public record UserBrief(String userId, String pageId, String nickname, String avatarUrl) {}

  public record LastMessage(String messageId, String msgType, String text, String createdAt) {}

  public record ConversationItem(
      String conversationId,
      UserBrief peer,
      LastMessage lastMessage,
      long unreadCount,
      String updatedAt) {}

  public record ConversationList(List<ConversationItem> items) {}

  public record ConversationCreated(String conversationId) {}

  public record MessageItem(
      String messageId,
      String senderId,
      String msgType,
      JsonNode content,
      String createdAt) {}

  public record MessageList(List<MessageItem> messages) {}

  public record CreateRequest(String peerUserId) {}

  public record SendRequest(String msgType, JsonNode content) {}

  public record UnreadCount(long count) {}

  public record OpsConversationRow(
      String conversationId,
      UserBrief left,
      UserBrief right,
      String preview,
      String updatedAt) {}

  public record OpsConversationPage(List<OpsConversationRow> items, String nextCursor, boolean hasMore) {}

  public record OpsMessageRow(
      String messageId,
      String conversationId,
      UserBrief sender,
      String msgType,
      String preview,
      String imageUrl,
      String postId,
      boolean hidden,
      String createdAt) {}

  public record OpsMessagePage(List<OpsMessageRow> items, String nextCursor, boolean hasMore) {}

  /** Redis 频道里投递到哪个用户；浏览器帧不含 userId。 */
  public record PushFrame(String userId, String conversationId, MessageItem payload) {}
}
