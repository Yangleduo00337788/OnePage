package com.onepage.content.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 给 user-service 拉某作者主页笔记。公网走 `/api/v1/users/{pageId}/posts`。 */
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
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
    return postService.listByAuthor(authorId, viewerId, cursor, limit);
  }
}
