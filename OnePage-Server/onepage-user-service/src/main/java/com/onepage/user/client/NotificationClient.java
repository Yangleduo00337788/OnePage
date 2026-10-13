package com.onepage.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 新关注投递。通知服务未启动时由调用方吞异常。 */
@FeignClient(name = "onepage-notification-service")
public interface NotificationClient {
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
}
