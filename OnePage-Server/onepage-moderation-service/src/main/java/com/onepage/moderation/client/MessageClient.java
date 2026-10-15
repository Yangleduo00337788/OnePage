package com.onepage.moderation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 运营巡查私信。更新用 POST，不要 PATCH。 */
@FeignClient(name = "onepage-message-service")
public interface MessageClient {
  @GetMapping("/internal/im/ops/conversations")
  OpsConversationPage conversations(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @GetMapping("/internal/im/ops/conversations/{conversationId}/messages")
  OpsMessagePage messages(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("conversationId") long conversationId,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @PostMapping("/internal/im/ops/messages/{messageId}/hide")
  void hide(@RequestHeader("X-Internal-Token") String token, @PathVariable("messageId") long messageId);

  @PostMapping("/internal/im/ops/messages/{messageId}/restore")
  void restore(@RequestHeader("X-Internal-Token") String token, @PathVariable("messageId") long messageId);

  record UserBrief(String userId, String pageId, String nickname, String avatarUrl) {}

  record OpsConversationRow(
      String conversationId, UserBrief left, UserBrief right, String preview, String updatedAt) {}

  record OpsConversationPage(List<OpsConversationRow> items, String nextCursor, boolean hasMore) {}

  record OpsMessageRow(
      String messageId,
      String conversationId,
      UserBrief sender,
      String msgType,
      String preview,
      String imageUrl,
      String postId,
      boolean hidden,
      String createdAt) {}

  record OpsMessagePage(List<OpsMessageRow> items, String nextCursor, boolean hasMore) {}
}
