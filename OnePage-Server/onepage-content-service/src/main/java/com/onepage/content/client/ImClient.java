package com.onepage.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/** 分享笔记卡片：content 编排快照，message 落库。 */
@FeignClient(name = "onepage-message-service")
public interface ImClient {
  @PostMapping("/internal/im/share-card")
  ShareResult shareCard(
      @RequestHeader("X-Internal-Token") String token,
      @RequestHeader("X-User-Id") String senderId,
      @RequestBody ShareCardRequest body);

  record ShareCardRequest(String conversationId, String targetUserId, Map<String, String> content) {}

  record ShareResult(String messageId) {}
}
