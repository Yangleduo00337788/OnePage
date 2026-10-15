package com.onepage.moderation.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 下架/恢复帖、工单标题、运营预览：改/读 content 库。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @PostMapping("/internal/posts/{postId}/takedown")
  void takedown(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @PostMapping("/internal/posts/{postId}/restore")
  void restore(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @GetMapping("/internal/posts/{postId}/ops")
  OpsPostView opsPost(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @GetMapping("/internal/posts/briefs")
  List<PostBrief> postBriefs(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("ids") List<Long> ids);

  record PostBrief(String postId, String title, String authorId, boolean takenDown) {}

  record MediaItem(String mediaId, String url, String mimeType, String role) {}

  record OpsPostView(
      String postId,
      UserSummary author,
      String title,
      String body,
      String coverUrl,
      String postType,
      String category,
      boolean original,
      String contentKind,
      String eventTitle,
      String visibility,
      List<MediaItem> media,
      boolean takenDown,
      String publishedAt) {}
}
