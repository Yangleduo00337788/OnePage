package com.onepage.moderation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 下架帖、工单标题：改/读 content 库。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @PostMapping("/internal/posts/{postId}/takedown")
  void takedown(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @GetMapping("/internal/posts/briefs")
  List<PostBrief> postBriefs(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("ids") List<Long> ids);

  record PostBrief(String postId, String title, String authorId) {}
}
