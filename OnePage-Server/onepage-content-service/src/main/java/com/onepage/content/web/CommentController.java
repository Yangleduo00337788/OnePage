package com.onepage.content.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.content.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {
  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable("commentId") long commentId) {
    commentService.delete(CurrentUser.requireUserId(request), commentId);
    return ResponseEntity.noContent().build();
  }
}
