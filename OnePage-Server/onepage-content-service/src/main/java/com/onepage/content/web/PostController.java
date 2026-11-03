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
import org.springframework.web.bind.annotation.PatchMapping;
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
        specOf(body));
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping("/mine")
  public FeedDtos.CursorPage mine(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return postService.listMine(CurrentUser.requireUserId(request), cursor, limit);
  }

  @PatchMapping("/{postId}")
  public FeedDtos.PostDetail update(
      HttpServletRequest request,
      @PathVariable("postId") long postId,
      @Valid @RequestBody CreateRequest body) {
    return postService.update(
        CurrentUser.requireUserId(request),
        postId,
        specOf(body));
  }

  @PatchMapping("/{postId}/visibility")
  public FeedDtos.PostBrief visibility(
      HttpServletRequest request,
      @PathVariable("postId") long postId,
      @RequestBody VisibilityRequest body) {
    return postService.setVisibility(
        CurrentUser.requireUserId(request),
        postId,
        body == null ? null : body.visibility());
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

  @PostMapping("/{postId}/play-complete")
  public ResponseEntity<Void> playComplete(HttpServletRequest request, @PathVariable("postId") long postId) {
    postService.recordPlayComplete(CurrentUser.requireUserId(request), postId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{postId}/share/link")
  public ShareLinkResult shareLink(HttpServletRequest request, @PathVariable("postId") long postId) {
    CurrentUser.requireUserId(request);
    return new ShareLinkResult(postService.shareLink(postId, CurrentUser.requireUserId(request)));
  }

  @PostMapping("/{postId}/share/im")
  public ResponseEntity<ShareImResult> shareIm(
      HttpServletRequest request,
      @PathVariable("postId") long postId,
      @RequestBody ShareImRequest body) {
    String messageId = postService.shareIm(
        CurrentUser.requireUserId(request),
        postId,
        body == null ? null : body.conversationId(),
        body == null ? null : body.targetUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(new ShareImResult(messageId));
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
        CurrentUser.requireUserId(request),
        postId,
        body.content(),
        body.parentId(),
        body.mentionPageIds());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  public record CreateRequest(
      String title,
      String body,
      List<String> mediaIds,
      @NotBlank String postType,
      String category,
      Boolean original,
      String contentKind,
      String eventId,
      String visibility,
      Boolean draft,
      String scheduledAt) {}

  private static FeedDtos.PublishSpec specOf(CreateRequest body) {
    return new FeedDtos.PublishSpec(
        body.title(),
        body.body(),
        body.mediaIds(),
        body.postType(),
        body.category(),
        body.original(),
        body.contentKind(),
        body.eventId(),
        body.visibility(),
        body.draft(),
        body.scheduledAt());
  }

  public record CreateCommentRequest(@NotBlank String content, String parentId, List<String> mentionPageIds) {}

  public record ShareLinkResult(String shareUrl) {}

  public record ShareImRequest(String conversationId, String targetUserId) {}

  public record ShareImResult(String messageId) {}

  public record VisibilityRequest(String visibility) {}
}
