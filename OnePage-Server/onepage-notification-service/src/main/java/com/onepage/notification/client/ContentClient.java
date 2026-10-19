package com.onepage.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 通知列表右侧笔记封面。name 必须等于 content 在 Nacos 的应用名。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @GetMapping("/internal/posts/briefs")
  List<PostBrief> postBriefs(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("ids") List<Long> ids);

  record PostBrief(String postId, String title, String authorId, boolean takenDown, String coverUrl) {}
}
