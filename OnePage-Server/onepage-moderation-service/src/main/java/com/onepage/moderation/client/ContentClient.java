package com.onepage.moderation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 下架帖：改 content 库，本服务不写 post 表。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @PostMapping("/internal/posts/{postId}/takedown")
  void takedown(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);
}
