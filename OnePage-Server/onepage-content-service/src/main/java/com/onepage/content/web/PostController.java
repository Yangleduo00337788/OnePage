package com.onepage.content.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.CommentService;
import com.onepage.content.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {
  private final PostService postService;
  private final CommentService commentService;

  public PostController(PostService postService, CommentService commentService) {
    this.postService = postService;
    this.commentService = commentService;
  }

  @PostMapping
  public ResponseEntity<FeedDtos.CreateResult> create(HttpServletRequest request, @Valid @RequestBody CreateRequest body) {
    FeedDtos.CreateResult result = postService.publish(
        CurrentUser.requireUserId(request),
        new FeedDtos.PublishSpec(
            body.title(),
            body.body(),
            body.mediaIds(),
            body.postType(),
            body.category(),
            body.original(),
            body.contentKind(),
            body.eventId(),
            body.visibility()));
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping("/{postId}")
  public FeedDtos.PostDetail detail(HttpServletRequest request, @PathVariable("postId") long postId) {
    return postService.detail(CurrentUser.tryUserId(request), postId);
  }

  @DeleteMapping("/{postId}")
  public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable("postId") long postId) {
    postService.softDelete(CurrentUser.requireUserId(request), postId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{postId}/view")
  public ResponseEntity<Void> view(HttpServletRequest request, @PathVariable("postId") long postId) {
    postService.recordView(CurrentUser.requireUserId(request), postId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{postId}/share/link")
  public ShareLinkResult shareLink(HttpServletRequest request, @PathVariable("postId") long postId) {
    CurrentUser.requireUserId(request);
    return new ShareLinkResult(postService.shareLink(postId, CurrentUser.requireUserId(request)));
  }

  @GetMapping("/{postId}/comments")
  public FeedDtos.CommentPage comments(
      HttpServletRequest request,
      @PathVariable("postId") long postId,
      @RequestParam(value = "rootId", required = false) String rootId,
      @RequestParam(value = "offset", required = false) Integer offset,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return commentService.list(CurrentUser.tryUserId(request), postId, rootId, offset, limit);
  }

  @PostMapping("/{postId}/comments")
  public ResponseEntity<FeedDtos.CommentView> createComment(
      HttpServletRequest request,
      @PathVariable("postId") long postId,
      @Valid @RequestBody CreateCommentRequest body) {
    FeedDtos.CommentView view = commentService.create(
        CurrentUser.requireUserId(request), postId, body.content(), body.parentId());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  public record CreateRequest(
      String title,
      @NotBlank String body,
      List<String> mediaIds,
      @NotBlank String postType,
      @NotBlank String category,
      Boolean original,
      String contentKind,
      String eventId,
      String visibility) {}

  public record CreateCommentRequest(@NotBlank String content, String parentId, List<String> mentionPageIds) {}

  public record ShareLinkResult(String shareUrl) {}
}
