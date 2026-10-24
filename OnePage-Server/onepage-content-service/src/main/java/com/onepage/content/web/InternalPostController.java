package com.onepage.content.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.PostService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
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

  @GetMapping("/users/{userId}/history-ids")
  public List<String> historyIds(HttpServletRequest request, @PathVariable("userId") long userId) {
    assertInternal(request);
    return postService.listHistoryIds(userId);
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

  @DeleteMapping("/users/{userId}/history/{postId}")
  public void deleteHistory(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @PathVariable("postId") long postId) {
    assertInternal(request);
    postService.deleteHistory(userId, postId);
  }

  @DeleteMapping("/users/{userId}/history")
  public void clearHistory(HttpServletRequest request, @PathVariable("userId") long userId) {
    assertInternal(request);
    postService.clearHistory(userId);
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

  @GetMapping("/search/posts")
  public FeedDtos.CursorPage searchable(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return postService.listSearchable(cursor, limit);
  }

  @GetMapping("/posts/briefs")
  public java.util.List<FeedDtos.OpsPostBrief> postBriefs(
      HttpServletRequest request,
      @RequestParam("ids") java.util.List<Long> ids) {
    assertInternal(request);
    return postService.opsBriefs(ids);
  }

  @GetMapping("/posts/ops")
  public FeedDtos.OpsPostPage opsPosts(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "taken", required = false) String taken,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return postService.opsPage(q, taken, cursor, limit);
  }

  @GetMapping("/posts/{postId}/ops")
  public FeedDtos.OpsPostView opsDetail(HttpServletRequest request, @PathVariable("postId") long postId) {
    assertInternal(request);
    return postService.opsDetail(postId);
  }

  @PostMapping("/posts/{postId}/takedown")
  public void takedown(HttpServletRequest request, @PathVariable("postId") long postId) {
    assertInternal(request);
    postService.opsTakedown(postId);
  }

  @PostMapping("/posts/{postId}/restore")
  public void restore(HttpServletRequest request, @PathVariable("postId") long postId) {
    assertInternal(request);
    postService.opsRestore(postId);
  }

  @PostMapping("/posts/{postId}/approve")
  public void approve(HttpServletRequest request, @PathVariable("postId") long postId) {
    assertInternal(request);
    postService.opsApprove(postId);
  }

  @PostMapping("/posts/{postId}/reject")
  public void reject(
      HttpServletRequest request,
      @PathVariable("postId") long postId,
      @RequestParam(value = "reason", required = false) String reason,
      @RequestParam(value = "handlerId", required = false) Long handlerId) {
    assertInternal(request);
    postService.opsReject(postId, reason, handlerId);
  }

  @PostMapping("/posts/{postId}/recall-review")
  public void recallReview(HttpServletRequest request, @PathVariable("postId") long postId) {
    assertInternal(request);
    postService.recallForReport(postId);
  }

  private void assertInternal(HttpServletRequest request) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
  }
}
