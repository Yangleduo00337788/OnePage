package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Comment;
import com.onepage.content.entity.CommentLike;
import com.onepage.content.entity.Post;
import com.onepage.content.entity.PostFavorite;
import com.onepage.content.entity.PostLike;
import com.onepage.content.mapper.CommentLikeMapper;
import com.onepage.content.mapper.CommentMapper;
import com.onepage.content.mapper.PostFavoriteMapper;
import com.onepage.content.mapper.PostLikeMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 赞藏。唯一键防重复；评论点赞 POST 切换，DELETE 取消。 */
@Service
public class ReactionService {
  private final PostService postService;
  private final PostLikeMapper likeMapper;
  private final PostFavoriteMapper favoriteMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final InteractionNotifier notifier;

  public ReactionService(
      PostService postService,
      PostLikeMapper likeMapper,
      PostFavoriteMapper favoriteMapper,
      CommentMapper commentMapper,
      CommentLikeMapper commentLikeMapper,
      InteractionNotifier notifier) {
    this.postService = postService;
    this.likeMapper = likeMapper;
    this.favoriteMapper = favoriteMapper;
    this.commentMapper = commentMapper;
    this.commentLikeMapper = commentLikeMapper;
    this.notifier = notifier;
  }

  public FeedDtos.LikeResult likePost(long userId, long postId) {
    Post post = postService.requireVisible(postId, userId);
    if (!liked(userId, postId)) {
      PostLike row = new PostLike();
      row.setUserId(userId);
      row.setPostId(postId);
      row.setCreatedAt(LocalDateTime.now());
      try {
        likeMapper.insert(row);
        notifier.emit(
            post.getAuthorId(),
            "like_post",
            userId,
            "like_post:" + postId,
            postId,
            null);
      } catch (DataIntegrityViolationException ignored) {
        // 并发重复点赞当成功
      }
    }
    return new FeedDtos.LikeResult(true, countLikes(postId));
  }

  public FeedDtos.LikeResult unlikePost(long userId, long postId) {
    postService.requireVisible(postId, userId);
    likeMapper.delete(Wrappers.<PostLike>lambdaQuery()
        .eq(PostLike::getUserId, userId)
        .eq(PostLike::getPostId, postId));
    return new FeedDtos.LikeResult(false, countLikes(postId));
  }

  public FeedDtos.FavoriteResult favoritePost(long userId, long postId) {
    Post post = postService.requireVisible(postId, userId);
    if (!favorited(userId, postId)) {
      PostFavorite row = new PostFavorite();
      row.setUserId(userId);
      row.setPostId(postId);
      row.setCreatedAt(LocalDateTime.now());
      try {
        favoriteMapper.insert(row);
        notifier.emit(
            post.getAuthorId(),
            "favorite_post",
            userId,
            "favorite_post:" + postId,
            postId,
            null);
      } catch (DataIntegrityViolationException ignored) {
        // 并发重复收藏当成功
      }
    }
    return new FeedDtos.FavoriteResult(true, countFavorites(postId));
  }

  public FeedDtos.FavoriteResult unfavoritePost(long userId, long postId) {
    postService.requireVisible(postId, userId);
    favoriteMapper.delete(Wrappers.<PostFavorite>lambdaQuery()
        .eq(PostFavorite::getUserId, userId)
        .eq(PostFavorite::getPostId, postId));
    return new FeedDtos.FavoriteResult(false, countFavorites(postId));
  }

  @Transactional
  public FeedDtos.LikeResult unlikeComment(long userId, long commentId) {
    Comment comment = requireOpenComment(commentId);
    int removed = commentLikeMapper.delete(Wrappers.<CommentLike>lambdaQuery()
        .eq(CommentLike::getUserId, userId)
        .eq(CommentLike::getCommentId, commentId));
    int count = n(comment.getLikeCount());
    if (removed > 0) {
      count = Math.max(0, count - 1);
      comment.setLikeCount(count);
      commentMapper.updateById(comment);
    }
    return new FeedDtos.LikeResult(false, count);
  }

  @Transactional
  public FeedDtos.LikeResult toggleCommentLike(long userId, long commentId) {
    Comment comment = requireOpenComment(commentId);
    boolean already = commentLikeMapper.selectCount(Wrappers.<CommentLike>lambdaQuery()
        .eq(CommentLike::getUserId, userId)
        .eq(CommentLike::getCommentId, commentId)) > 0;
    if (already) {
      return unlikeComment(userId, commentId);
    }
    CommentLike row = new CommentLike();
    row.setUserId(userId);
    row.setCommentId(commentId);
    row.setCreatedAt(LocalDateTime.now());
    try {
      commentLikeMapper.insert(row);
      comment.setLikeCount(n(comment.getLikeCount()) + 1);
      commentMapper.updateById(comment);
      notifier.emit(
          comment.getUserId(),
          "like_comment",
          userId,
          "like_comment:" + commentId,
          comment.getPostId(),
          commentId);
    } catch (DataIntegrityViolationException ignored) {
      comment = commentMapper.selectById(commentId);
    }
    return new FeedDtos.LikeResult(true, n(comment.getLikeCount()));
  }

  private boolean liked(long userId, long postId) {
    return likeMapper.selectCount(Wrappers.<PostLike>lambdaQuery()
        .eq(PostLike::getUserId, userId)
        .eq(PostLike::getPostId, postId)) > 0;
  }

  private boolean favorited(long userId, long postId) {
    return favoriteMapper.selectCount(Wrappers.<PostFavorite>lambdaQuery()
        .eq(PostFavorite::getUserId, userId)
        .eq(PostFavorite::getPostId, postId)) > 0;
  }

  private long countLikes(long postId) {
    return likeMapper.selectCount(Wrappers.<PostLike>lambdaQuery().eq(PostLike::getPostId, postId));
  }

  private long countFavorites(long postId) {
    return favoriteMapper.selectCount(Wrappers.<PostFavorite>lambdaQuery().eq(PostFavorite::getPostId, postId));
  }

  private Comment requireOpenComment(long commentId) {
    Comment comment = commentMapper.selectById(commentId);
    if (comment == null || comment.getStatus() == null || comment.getStatus() != 0) {
      throw BusinessException.notFound("评论不存在");
    }
    return comment;
  }

  private int n(Integer v) {
    return v == null ? 0 : v;
  }
}
