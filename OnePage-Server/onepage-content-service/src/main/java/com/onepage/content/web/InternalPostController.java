package com.onepage.content.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 给 user-service 拉某作者主页笔记、本人赞藏浏览、作者获赞/被收藏。公网走 `/api/v1/users/...`。 */
@RestController
@RequestMapping("/internal")
public class InternalPostController {
  private final PostService postService;
  private final String expectedToken;

  public InternalPostController(
      PostService postService,
      @Value("${onepage.internal-token}") String expectedToken) {
    this.postService = postService;
    this.expectedToken = expectedToken;
  }

  @GetMapping("/authors/{authorId}/posts")
  public FeedDtos.CursorPage byAuthor(
      HttpServletRequest request,
      @PathVariable("authorId") long authorId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "viewerId", required = false) Long viewerId) {
    assertInternal(request);
    return postService.listByAuthor(authorId, viewerId, cursor, limit);
  }

  @GetMapping("/users/{userId}/favorites")
  public FeedDtos.CursorPage favorites(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return postService.listFavorites(userId, cursor, limit);
  }

  @GetMapping("/users/{userId}/likes")
  public FeedDtos.CursorPage likes(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return postService.listLikes(userId, cursor, limit);
  }

  @GetMapping("/users/{userId}/history")
  public FeedDtos.CursorPage history(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return postService.listHistory(userId, cursor, limit);
  }

  @GetMapping("/users/{userId}/likes-received")
  public FeedDtos.ReceivedPage likesReceived(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return postService.listLikesReceived(userId, cursor, limit);
  }

  @GetMapping("/users/{userId}/favorites-received")
  public FeedDtos.ReceivedPage favoritesReceived(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return postService.listFavoritesReceived(userId, cursor, limit);
  }

  @GetMapping("/posts/briefs")
  public java.util.List<FeedDtos.OpsPostBrief> postBriefs(
      HttpServletRequest request,
      @RequestParam("ids") java.util.List<Long> ids) {
    assertInternal(request);
    return postService.opsBriefs(ids);
  }

  @PostMapping("/posts/{postId}/takedown")
  public void takedown(HttpServletRequest request, @PathVariable("postId") long postId) {
    assertInternal(request);
    postService.opsTakedown(postId);
  }

  private void assertInternal(HttpServletRequest request) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
  }
}
