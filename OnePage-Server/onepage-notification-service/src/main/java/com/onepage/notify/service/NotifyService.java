package com.onepage.notify.service;

import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.notify.client.UserClient;
import com.onepage.notify.dto.NotifyDtos;
import com.onepage.notify.dto.TypeCount;
import com.onepage.notify.entity.NotificationRow;
import com.onepage.notify.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收件箱。投递按 aggregate_key 合并为一条并重新标未读。
 * actor 批量向 user-service 拉摘要，避免列表 N+1。
 */
@Service
public class NotifyService {
  private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
  private static final Set<String> TYPES = Set.of(
      "comment", "reply", "mention", "like_post", "favorite_post", "like_comment", "follow");
  private static final List<String> GROUP_COMMENT = List.of("comment", "reply", "mention");
  private static final List<String> GROUP_REACTION = List.of("like_post", "favorite_post", "like_comment");
  private static final List<String> GROUP_FOLLOW = List.of("follow");

  private static final int PREVIEW_MAX = 120;

  private final NotificationMapper mapper;
  private final UserClient userClient;
  private final ObjectMapper objectMapper;
  private final String internalToken;

  public NotifyService(
      NotificationMapper mapper,
      UserClient userClient,
      ObjectMapper objectMapper,
      @Value("${onepage.internal-token}") String internalToken) {
    this.mapper = mapper;
    this.userClient = userClient;
    this.objectMapper = objectMapper;
    this.internalToken = internalToken;
  }

  public void ingest(NotifyDtos.IngestRequest body) {
    if (body == null || body.recipientId() == null || body.actorId() == null
        || !StringUtils.hasText(body.type()) || !StringUtils.hasText(body.aggregateKey())) {
      throw BusinessException.validation("通知字段不完整");
    }
    if (!TYPES.contains(body.type())) {
      throw BusinessException.validation("通知类型不合法");
    }
    if (body.recipientId().equals(body.actorId())) {
      return;
    }
    mapper.upsert(
        IdGenerator.nextId(),
        body.recipientId(),
        body.type(),
        body.actorId(),
        body.aggregateKey().trim(),
        body.postId(),
        body.commentId(),
        toPayload(body.preview()),
        LocalDateTime.now());
  }

  public NotifyDtos.CursorPage page(long userId, String group, String cursor, Integer limit, Boolean unreadOnly) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    List<String> types = typesOf(group);
    LocalDateTime cursorAt = null;
    Long cursorId = null;
    if (StringUtils.hasText(cursor)) {
      String[] parts = cursor.split("_", 2);
      if (parts.length != 2) {
        throw BusinessException.validation("cursor 不合法");
      }
      try {
        cursorAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneId.systemDefault());
        cursorId = Long.parseLong(parts[1]);
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("cursor 不合法");
      }
    }
    List<NotificationRow> rows = mapper.page(
        userId, types, Boolean.TRUE.equals(unreadOnly), cursorAt, cursorId, size + 1);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    Map<Long, UserSummary> actors = loadActors(rows);
    List<NotifyDtos.NotificationItem> items = new ArrayList<>();
    for (NotificationRow row : rows) {
      items.add(toItem(row, actors.get(row.getActorId())));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      NotificationRow last = rows.getLast();
      if (last.getCreatedAt() != null && last.getId() != null) {
        next = last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + "_" + last.getId();
      }
    }
    return new NotifyDtos.CursorPage(items, next, hasMore);
  }

  public NotifyDtos.UnreadCount unread(long userId) {
    long comment = 0;
    long reaction = 0;
    long follow = 0;
    List<TypeCount> rows = mapper.countUnreadByType(userId);
    if (rows != null) {
      for (TypeCount row : rows) {
        if (row == null || row.getType() == null) {
          continue;
        }
        long n = row.getCnt();
        if (GROUP_COMMENT.contains(row.getType())) {
          comment += n;
        } else if (GROUP_REACTION.contains(row.getType())) {
          reaction += n;
        } else if (GROUP_FOLLOW.contains(row.getType())) {
          follow += n;
        }
      }
    }
    return new NotifyDtos.UnreadCount(comment + reaction + follow, comment, reaction, follow);
  }

  public void markRead(long userId, NotifyDtos.ReadRequest body) {
    if (body == null) {
      throw BusinessException.validation("请求体不能为空");
    }
    LocalDateTime now = LocalDateTime.now();
    if (Boolean.TRUE.equals(body.all())) {
      mapper.markAllRead(userId, now);
      return;
    }
    if (body.ids() == null || body.ids().isEmpty()) {
      throw BusinessException.validation("ids 或 all 必填其一");
    }
    List<Long> ids = new ArrayList<>();
    for (String raw : body.ids()) {
      try {
        ids.add(Long.parseLong(raw));
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("ids 不合法");
      }
    }
    mapper.markRead(userId, ids, now);
  }

  private List<String> typesOf(String group) {
    if (!StringUtils.hasText(group)) {
      return List.of();
    }
    return switch (group) {
      case "comment" -> GROUP_COMMENT;
      case "reaction" -> GROUP_REACTION;
      case "follow" -> GROUP_FOLLOW;
      default -> throw BusinessException.validation("group 不合法");
    };
  }

  private Map<Long, UserSummary> loadActors(List<NotificationRow> rows) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    for (NotificationRow row : rows) {
      if (row.getActorId() != null) {
        ids.add(row.getActorId());
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    try {
      List<UserSummary> briefs = userClient.briefs(internalToken, new ArrayList<>(ids));
      if (briefs == null) {
        return Map.of();
      }
      return briefs.stream()
          .filter(b -> b != null && b.userId() != null)
          .collect(Collectors.toMap(b -> Long.parseLong(b.userId()), Function.identity(), (a, b) -> a));
    } catch (RuntimeException ex) {
      log.warn("load notify actors failed", ex);
      return Map.of();
    }
  }

  private NotifyDtos.NotificationItem toItem(NotificationRow row, UserSummary actor) {
    NotifyDtos.UserBrief brief = actor == null
        ? new NotifyDtos.UserBrief(
            row.getActorId() == null ? "" : String.valueOf(row.getActorId()), "", "用户", "")
        : new NotifyDtos.UserBrief(actor.userId(), actor.pageId(), actor.nickname(), actor.avatarUrl());
    return new NotifyDtos.NotificationItem(
        String.valueOf(row.getId()),
        row.getType(),
        brief,
        row.getPostId() == null ? null : String.valueOf(row.getPostId()),
        row.getCommentId() == null ? null : String.valueOf(row.getCommentId()),
        previewOf(row.getPayload()),
        row.getReadAt() != null,
        row.getCreatedAt() == null ? "" : row.getCreatedAt().toString());
  }

  private String toPayload(String preview) {
    if (!StringUtils.hasText(preview)) {
      return null;
    }
    String text = preview.trim();
    if (text.length() > PREVIEW_MAX) {
      text = text.substring(0, PREVIEW_MAX);
    }
    try {
      return objectMapper.writeValueAsString(Map.of("preview", text));
    } catch (Exception ex) {
      log.warn("notify payload serialize failed");
      return null;
    }
  }

  private String previewOf(String payload) {
    if (!StringUtils.hasText(payload)) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(payload);
      JsonNode preview = node.get("preview");
      return preview == null || preview.isNull() ? null : preview.asText();
    } catch (Exception ex) {
      return null;
    }
  }
}
