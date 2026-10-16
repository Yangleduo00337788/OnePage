package com.onepage.content.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运营隐藏/恢复评论。 */
@RestController
@RequestMapping("/internal")
public class InternalCommentController {
  private final CommentService commentService;
  private final String expectedToken;

  public InternalCommentController(
      CommentService commentService,
      @Value("${onepage.internal-token}") String expectedToken) {
    this.commentService = commentService;
    this.expectedToken = expectedToken;
  }

  @GetMapping("/comments/briefs")
  public java.util.List<FeedDtos.OpsCommentRow> briefs(
      HttpServletRequest request, @RequestParam("ids") java.util.List<Long> ids) {
    assertInternal(request);
    return commentService.opsBriefs(ids);
  }

  @GetMapping("/comments/ops")
  public FeedDtos.OpsCommentPage opsComments(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return commentService.opsPage(q, hidden, cursor, limit);
  }

  @PostMapping("/comments/{commentId}/hide")
  public void hide(HttpServletRequest request, @PathVariable("commentId") long commentId) {
    assertInternal(request);
    commentService.opsHide(commentId);
  }

  @PostMapping("/comments/{commentId}/restore")
  public void restore(HttpServletRequest request, @PathVariable("commentId") long commentId) {
    assertInternal(request);
    commentService.opsRestore(commentId);
  }

  private void assertInternal(HttpServletRequest request) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
  }
}
