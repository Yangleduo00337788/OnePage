package com.onepage.content.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.ReactionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reactions")
public class ReactionController {
  private final ReactionService reactionService;

  public ReactionController(ReactionService reactionService) {
    this.reactionService = reactionService;
  }

  @PostMapping("/posts/{postId}/like")
  public FeedDtos.LikeResult like(HttpServletRequest request, @PathVariable("postId") long postId) {
    return reactionService.likePost(CurrentUser.requireUserId(request), postId);
  }

  @DeleteMapping("/posts/{postId}/like")
  public FeedDtos.LikeResult unlike(HttpServletRequest request, @PathVariable("postId") long postId) {
    return reactionService.unlikePost(CurrentUser.requireUserId(request), postId);
  }

  @PostMapping("/posts/{postId}/favorite")
  public FeedDtos.FavoriteResult favorite(HttpServletRequest request, @PathVariable("postId") long postId) {
    return reactionService.favoritePost(CurrentUser.requireUserId(request), postId);
  }

  @DeleteMapping("/posts/{postId}/favorite")
  public FeedDtos.FavoriteResult unfavorite(HttpServletRequest request, @PathVariable("postId") long postId) {
    return reactionService.unfavoritePost(CurrentUser.requireUserId(request), postId);
  }

  @PostMapping("/comments/{commentId}/like")
  public FeedDtos.LikeResult likeComment(HttpServletRequest request, @PathVariable("commentId") long commentId) {
    return reactionService.toggleCommentLike(CurrentUser.requireUserId(request), commentId);
  }
}
