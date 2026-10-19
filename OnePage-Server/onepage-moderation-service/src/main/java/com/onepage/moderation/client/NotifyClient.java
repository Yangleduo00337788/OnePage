package com.onepage.moderation.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 运营看某用户收件箱。删除用 POST，不要 PATCH。 */
@FeignClient(name = "onepage-notification-service")
public interface NotifyClient {
  @GetMapping("/internal/notifications/ops")
  NotifyPage page(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("userId") long userId,
      @RequestParam(value = "group", required = false) String group,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @PostMapping("/internal/notifications/{id}/hide")
  void hide(@RequestHeader("X-Internal-Token") String token, @PathVariable("id") long id);

  @PostMapping("/internal/notifications")
  void ingest(@RequestHeader("X-Internal-Token") String token, @RequestBody IngestRequest body);

  record IngestRequest(
      Long recipientId,
      String type,
      Long actorId,
      String aggregateKey,
      Long postId,
      Long commentId,
      String preview) {}

  record NotifyItem(
      String id,
      String type,
      UserSummary actor,
      String postId,
      String commentId,
      String preview,
      boolean read,
      String createdAt) {}

  record NotifyPage(List<NotifyItem> items, String nextCursor, boolean hasMore) {}
}
