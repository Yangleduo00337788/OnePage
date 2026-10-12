package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Comment;
import com.onepage.content.entity.CommentLike;
import com.onepage.content.entity.Post;
import com.onepage.content.mapper.CommentLikeMapper;
import com.onepage.content.mapper.CommentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CommentService {
  private final PostService postService;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final AuthorLookup authorLookup;

  public CommentService(
      PostService postService,
      CommentMapper commentMapper,
      CommentLikeMapper commentLikeMapper,
      AuthorLookup authorLookup) {
    this.postService = postService;
    this.commentMapper = commentMapper;
    this.commentLikeMapper = commentLikeMapper;
    this.authorLookup = authorLookup;
  }

  public FeedDtos.CommentPage list(Long viewerId, long postId, String rootId, Integer offset, Integer limit) {
    postService.requireVisible(postId, viewerId);
    int skip = offset == null ? 0 : Math.max(offset, 0);
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LambdaQueryWrapper<Comment> query = Wrappers.<Comment>lambdaQuery()
        .eq(Comment::getPostId, postId)
        .eq(Comment::getStatus, 0)
        .orderByDesc(Comment::getCreatedAt)
        .last("LIMIT " + skip + "," + size);
    if (StringUtils.hasText(rootId)) {
      long root = parseId(rootId, "rootId");
      query.eq(Comment::getRootId, root).ne(Comment::getId, root);
    } else {
      query.isNull(Comment::getParentId);
    }
    List<Comment> rows = commentMapper.selectList(query);
    LambdaQueryWrapper<Comment> countQuery = Wrappers.<Comment>lambdaQuery()
        .eq(Comment::getPostId, postId)
        .eq(Comment::getStatus, 0);
    if (StringUtils.hasText(rootId)) {
      long root = parseId(rootId, "rootId");
      countQuery.eq(Comment::getRootId, root).ne(Comment::getId, root);
    } else {
      countQuery.isNull(Comment::getParentId);
    }
    long total = commentMapper.selectCount(countQuery);
    List<FeedDtos.CommentView> items = new ArrayList<>();
    for (Comment row : rows) {
      items.add(toView(viewerId, row));
    }
    return new FeedDtos.CommentPage(items, total);
  }

  @Transactional
  public FeedDtos.CommentView create(long userId, long postId, String content, String parentId) {
    Post post = postService.requireVisible(postId, userId);
    if (!StringUtils.hasText(content) || content.trim().length() > 2000) {
      throw BusinessException.validation("评论内容不合法");
    }
    LocalDateTime now = LocalDateTime.now();
    long id = IdGenerator.nextId();
    Comment row = new Comment();
    row.setId(id);
    row.setPostId(post.getId());
    row.setUserId(userId);
    row.setContent(content.trim());
    row.setLikeCount(0);
    row.setStatus(0);
    row.setCreatedAt(now);
    if (!StringUtils.hasText(parentId)) {
      row.setParentId(null);
      row.setRootId(id);
    } else {
      Comment parent = commentMapper.selectById(parseId(parentId, "parentId"));
      if (parent == null || !postIdEquals(parent.getPostId(), postId) || parent.getStatus() == null || parent.getStatus() != 0) {
        throw BusinessException.notFound("父评论不存在");
      }
      row.setParentId(parent.getId());
      row.setRootId(parent.getRootId() == null ? parent.getId() : parent.getRootId());
    }
    commentMapper.insert(row);
    return toView(userId, row);
  }

  public void delete(long userId, long commentId) {
    Comment row = commentMapper.selectById(commentId);
    if (row == null || row.getStatus() == null || row.getStatus() != 0) {
      throw BusinessException.notFound("评论不存在");
    }
    Post post = postService.requireVisible(row.getPostId(), userId);
    if ((row.getUserId() == null || row.getUserId() != userId)
        && (post.getAuthorId() == null || post.getAuthorId() != userId)) {
      throw BusinessException.forbidden("只能删除自己的评论");
    }
    row.setStatus(1);
    commentMapper.updateById(row);
  }

  private FeedDtos.CommentView toView(Long viewerId, Comment row) {
    long replies = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
        .eq(Comment::getRootId, row.getId())
        .ne(Comment::getId, row.getId())
        .eq(Comment::getStatus, 0));
    boolean liked = viewerId != null && commentLikeMapper.selectCount(Wrappers.<CommentLike>lambdaQuery()
        .eq(CommentLike::getUserId, viewerId)
        .eq(CommentLike::getCommentId, row.getId())) > 0;
    return new FeedDtos.CommentView(
        String.valueOf(row.getId()),
        authorLookup.brief(row.getUserId()),
        row.getContent(),
        row.getParentId() == null ? null : String.valueOf(row.getParentId()),
        String.valueOf(row.getRootId()),
        n(row.getLikeCount()),
        liked,
        replies,
        PostService.iso(row.getCreatedAt()));
  }

  private long parseId(String raw, String field) {
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      throw BusinessException.validation(field + " 不合法");
    }
  }

  private boolean postIdEquals(Long a, long b) {
    return a != null && a == b;
  }

  private long n(Integer v) {
    return v == null ? 0 : v;
  }
}
