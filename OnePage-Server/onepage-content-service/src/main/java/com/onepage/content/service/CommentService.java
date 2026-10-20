package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.client.UserClient;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Comment;
import com.onepage.content.entity.CommentLike;
import com.onepage.content.entity.Post;
import com.onepage.content.mapper.CommentLikeMapper;
import com.onepage.content.mapper.CommentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CommentService {
  private static final Logger log = LoggerFactory.getLogger(CommentService.class);
  private static final Pattern AT = Pattern.compile("@([^\\s@]{1,32})");
  private static final int MENTION_MAX = 10;

  private final PostService postService;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final AuthorLookup authorLookup;
  private final InteractionNotifier notifier;
  private final UserClient userClient;
  private final CommunityPolicyCache policyCache;
  private final String internalToken;

  public CommentService(
      PostService postService,
      CommentMapper commentMapper,
      CommentLikeMapper commentLikeMapper,
      AuthorLookup authorLookup,
      InteractionNotifier notifier,
      UserClient userClient,
      CommunityPolicyCache policyCache,
      @Value("${onepage.internal-token}") String internalToken) {
    this.postService = postService;
    this.commentMapper = commentMapper;
    this.commentLikeMapper = commentLikeMapper;
    this.authorLookup = authorLookup;
    this.notifier = notifier;
    this.userClient = userClient;
    this.policyCache = policyCache;
    this.internalToken = internalToken;
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
  public FeedDtos.CommentView create(
      long userId, long postId, String content, String parentId, List<String> mentionPageIds) {
    Post post = postService.requireVisible(postId, userId);
    if (!StringUtils.hasText(content) || content.trim().length() > 2000) {
      throw BusinessException.validation("评论内容不合法");
    }
    policyCache.assertClean(content);
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
    Comment parent = null;
    if (!StringUtils.hasText(parentId)) {
      row.setParentId(null);
      row.setRootId(id);
    } else {
      parent = commentMapper.selectById(parseId(parentId, "parentId"));
      if (parent == null || !postIdEquals(parent.getPostId(), postId) || parent.getStatus() == null || parent.getStatus() != 0) {
        throw BusinessException.notFound("父评论不存在");
      }
      row.setParentId(parent.getId());
      row.setRootId(parent.getRootId() == null ? parent.getId() : parent.getRootId());
    }
    commentMapper.insert(row);
    String preview = row.getContent();
    LinkedHashSet<Long> already = new LinkedHashSet<>();
    already.add(userId);
    if (parent == null) {
      notifier.emit(
          post.getAuthorId(),
          "comment",
          userId,
          "comment:" + id,
          post.getId(),
          id,
          preview);
      if (post.getAuthorId() != null) {
        already.add(post.getAuthorId());
      }
    } else {
      notifier.emit(
          parent.getUserId(),
          "reply",
          userId,
          "reply:" + id,
          post.getId(),
          id,
          preview);
      if (parent.getUserId() != null) {
        already.add(parent.getUserId());
      }
    }
    emitMentions(userId, post.getId(), id, preview, mentionPageIds, already);
    return toView(userId, row);
  }

  private void emitMentions(
      long actorId,
      long postId,
      long commentId,
      String preview,
      List<String> mentionPageIds,
      LinkedHashSet<Long> already) {
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    if (mentionPageIds != null) {
      for (String raw : mentionPageIds) {
        addMentionKey(keys, raw);
      }
    }
    if (StringUtils.hasText(preview)) {
      Matcher matcher = AT.matcher(preview);
      while (matcher.find()) {
        addMentionKey(keys, matcher.group(1));
      }
    }
    if (keys.isEmpty()) {
      return;
    }
    List<UserSummary> hits;
    try {
      hits = userClient.byKeys(internalToken, new ArrayList<>(keys));
    } catch (RuntimeException ex) {
      log.warn("resolve mention keys failed", ex);
      return;
    }
    if (hits == null) {
      return;
    }
    int sent = 0;
    for (UserSummary hit : hits) {
      if (hit == null || !StringUtils.hasText(hit.userId())) {
        continue;
      }
      long recipient;
      try {
        recipient = Long.parseLong(hit.userId());
      } catch (NumberFormatException ex) {
        continue;
      }
      if (!already.add(recipient)) {
        continue;
      }
      notifier.emit(
          recipient,
          "mention",
          actorId,
          "mention:" + commentId + ":" + recipient,
          postId,
          commentId,
          preview);
      sent++;
      if (sent >= MENTION_MAX) {
        break;
      }
    }
  }

  private static void addMentionKey(LinkedHashSet<String> keys, String raw) {
    if (!StringUtils.hasText(raw) || keys.size() >= 20) {
      return;
    }
    String key = raw.trim();
    if (key.startsWith("@")) {
      key = key.substring(1);
    }
    if (key.isEmpty() || key.length() > 32) {
      return;
    }
    keys.add(key);
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

  public List<FeedDtos.OpsCommentRow> opsBriefs(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<Comment> rows = commentMapper.selectBatchIds(ids);
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<FeedDtos.OpsCommentRow> items = new ArrayList<>();
    for (Comment row : rows) {
      items.add(toOpsRow(row));
    }
    return items;
  }

  /** 运营评论列表。hidden=yes 仅已隐藏，no 仅可见。 */
  public FeedDtos.OpsCommentPage opsPage(String q, String hidden, String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LambdaQueryWrapper<Comment> query = Wrappers.<Comment>lambdaQuery();
    if ("yes".equals(hidden)) {
      query.ne(Comment::getStatus, 0);
    } else if ("no".equals(hidden)) {
      query.eq(Comment::getStatus, 0);
    }
    if (StringUtils.hasText(q)) {
      String text = q.trim();
      query.and(w -> {
        w.like(Comment::getContent, text);
        try {
          long id = Long.parseLong(text);
          w.or().eq(Comment::getId, id).or().eq(Comment::getPostId, id);
        } catch (NumberFormatException ignored) {
          // 只按正文
        }
      });
    }
    if (StringUtils.hasText(cursor)) {
      String[] parts = cursor.split("_", 2);
      if (parts.length != 2) {
        throw BusinessException.validation("cursor 不合法");
      }
      try {
        LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneId.systemDefault());
        long id = Long.parseLong(parts[1]);
        query.and(w -> w.lt(Comment::getCreatedAt, at)
            .or(inner -> inner.eq(Comment::getCreatedAt, at).lt(Comment::getId, id)));
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("cursor 不合法");
      }
    }
    query.orderByDesc(Comment::getCreatedAt).orderByDesc(Comment::getId).last("LIMIT " + (size + 1));
    List<Comment> rows = commentMapper.selectList(query);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    List<FeedDtos.OpsCommentRow> items = new ArrayList<>();
    for (Comment row : rows) {
      items.add(toOpsRow(row));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      Comment last = rows.getLast();
      if (last.getCreatedAt() != null && last.getId() != null) {
        next = PostService.epochMilli(last.getCreatedAt()) + "_" + last.getId();
      }
    }
    return new FeedDtos.OpsCommentPage(items, next, hasMore);
  }

  @Transactional
  public void opsHide(long commentId) {
    Comment row = commentMapper.selectById(commentId);
    if (row == null) {
      throw BusinessException.notFound("评论不存在");
    }
    if (row.getStatus() != null && row.getStatus() != 0) {
      return;
    }
    row.setStatus(1);
    commentMapper.updateById(row);
  }

  @Transactional
  public void opsRestore(long commentId) {
    Comment row = commentMapper.selectById(commentId);
    if (row == null) {
      throw BusinessException.notFound("评论不存在");
    }
    row.setStatus(0);
    commentMapper.updateById(row);
  }

  private FeedDtos.OpsCommentRow toOpsRow(Comment row) {
    return new FeedDtos.OpsCommentRow(
        String.valueOf(row.getId()),
        row.getPostId() == null ? "" : String.valueOf(row.getPostId()),
        authorLookup.brief(row.getUserId()),
        row.getContent() == null ? "" : row.getContent(),
        row.getStatus() == null || row.getStatus() != 0,
        PostService.iso(row.getCreatedAt()));
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
