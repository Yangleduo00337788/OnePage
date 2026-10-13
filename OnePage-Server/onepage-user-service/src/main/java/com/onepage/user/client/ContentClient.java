package com.onepage.user.client;

import com.onepage.user.dto.PostCursorPage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** 通过 Nacos 发现 content-service：改头像核对媒体归属；主页笔记与本人赞藏浏览走内部列表。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @GetMapping("/internal/media/{mediaId}")
  MediaInfo getMedia(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("mediaId") Long mediaId);

  @GetMapping("/internal/authors/{authorId}/posts")
  PostCursorPage listAuthorPosts(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("authorId") long authorId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "viewerId", required = false) Long viewerId);

  @GetMapping("/internal/users/{userId}/favorites")
  PostCursorPage listFavorites(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @GetMapping("/internal/users/{userId}/likes")
  PostCursorPage listLikes(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @GetMapping("/internal/users/{userId}/history")
  PostCursorPage listHistory(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  record MediaInfo(Long mediaId, Long ownerId, String objectKey, Integer status) {}
}
