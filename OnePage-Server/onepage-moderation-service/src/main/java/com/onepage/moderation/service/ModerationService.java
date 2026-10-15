package com.onepage.moderation.service;

import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.moderation.client.AuthClient;
import com.onepage.moderation.client.ContentClient;
import com.onepage.moderation.client.UserClient;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.entity.Report;
import com.onepage.moderation.mapper.ReportMapper;
import com.onepage.moderation.web.StaffGuard;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 举报入库与处理。下架/冻结用 Feign，失败不把工单标成已处理。
 */
@Service
public class ModerationService {
  private static final Logger log = LoggerFactory.getLogger(ModerationService.class);
  private static final Set<String> TARGETS = Set.of("post", "user");
  private static final Set<String> ACTIONS = Set.of("dismiss", "takedown", "restore", "ban", "unban");
  private static final Set<String> REASONS = Set.of("spam", "porn", "abuse", "illegal", "other");
  private static final int STATUS_PENDING = 0;
  private static final int STATUS_DONE = 1;

  private final ReportMapper mapper;
  private final UserClient userClient;
  private final ContentClient contentClient;
  private final AuthClient authClient;
  private final StaffGuard staffGuard;
  private final String internalToken;

  public ModerationService(
      ReportMapper mapper,
      UserClient userClient,
      ContentClient contentClient,
      AuthClient authClient,
      StaffGuard staffGuard,
      @Value("${onepage.internal-token}") String internalToken) {
    this.mapper = mapper;
    this.userClient = userClient;
    this.contentClient = contentClient;
    this.authClient = authClient;
    this.staffGuard = staffGuard;
    this.internalToken = internalToken;
  }

  public ModerationDtos.CreateReportResult create(long reporterId, ModerationDtos.CreateReportRequest body) {
    if (body == null || !StringUtils.hasText(body.targetType()) || !StringUtils.hasText(body.targetId())
        || !StringUtils.hasText(body.reasonCode())) {
      throw BusinessException.validation("举报字段不完整");
    }
    String type = body.targetType().trim();
    if (!TARGETS.contains(type)) {
      throw BusinessException.validation("targetType 须为 post 或 user");
    }
    long targetId = parseId(body.targetId(), "targetId");
    if ("user".equals(type) && targetId == reporterId) {
      throw BusinessException.validation("不能举报自己");
    }
    String reason = body.reasonCode().trim();
    if (!REASONS.contains(reason)) {
      throw BusinessException.validation("原因须为 spam / porn / abuse / illegal / other");
    }
    String detail = blankToNull(body.detail());
    if (detail != null && detail.length() > 1000) {
      throw BusinessException.validation("说明过长");
    }
    Report row = new Report();
    long id = IdGenerator.nextId();
    row.setId(id);
    row.setReporterId(reporterId);
    row.setTargetType(type);
    row.setTargetId(targetId);
    row.setReasonCode(reason);
    row.setDetail(detail);
    row.setStatus(STATUS_PENDING);
    row.setCreatedAt(LocalDateTime.now());
    mapper.insert(row);
    return new ModerationDtos.CreateReportResult(String.valueOf(id));
  }

  public ModerationDtos.CursorPage page(String status, String cursor, Integer limit) {
    Integer statusCode = parseStatus(status);
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
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
    List<Report> rows = mapper.page(statusCode, cursorAt, cursorId, size + 1);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    Map<Long, ContentClient.PostBrief> posts = loadPosts(rows);
    Map<Long, UserSummary> people = loadPeople(rows, posts);
    List<ModerationDtos.OpsReport> items = new ArrayList<>();
    for (Report row : rows) {
      items.add(toView(row, people, posts));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      Report last = rows.getLast();
      if (last.getCreatedAt() != null && last.getId() != null) {
        next = last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + "_" + last.getId();
      }
    }
    return new ModerationDtos.CursorPage(items, next, hasMore);
  }

  @Transactional
  public ModerationDtos.ResolveResult resolve(long handlerId, long reportId, ModerationDtos.ResolveRequest body) {
    if (body == null || !StringUtils.hasText(body.action())) {
      throw BusinessException.validation("action 必填");
    }
    String action = body.action().trim();
    if (!ACTIONS.contains(action)) {
      throw BusinessException.validation("action 须为 dismiss / takedown / restore / ban / unban");
    }
    String note = blankToNull(body.note());
    if (note != null && note.length() > 500) {
      throw BusinessException.validation("备注过长");
    }
    Report row = mapper.selectById(reportId);
    if (row == null) {
      throw BusinessException.notFound("工单不存在");
    }
    boolean alreadyDone = row.getStatus() != null && row.getStatus() == STATUS_DONE;
    if (alreadyDone && "dismiss".equals(action)) {
      throw BusinessException.conflict("工单已处理");
    }
    applyAction(handlerId, row, action);
    LocalDateTime now = LocalDateTime.now();
    row.setStatus(STATUS_DONE);
    row.setHandlerId(handlerId);
    row.setHandleAction(action);
    row.setHandleNote(note);
    row.setHandledAt(now);
    mapper.updateById(row);
    log.warn("ops report {} action {} by {}", reportId, action, handlerId);
    return new ModerationDtos.ResolveResult(String.valueOf(reportId), "done");
  }

  public ModerationDtos.OpsPostView opsPost(long postId) {
    ContentClient.OpsPostView raw = contentClient.opsPost(internalToken, postId);
    if (raw == null) {
      throw BusinessException.notFound("笔记不存在");
    }
    List<ModerationDtos.ContentMedia> media = new ArrayList<>();
    if (raw.media() != null) {
      for (ContentClient.MediaItem item : raw.media()) {
        if (item == null) {
          continue;
        }
        media.add(new ModerationDtos.ContentMedia(item.mediaId(), item.url(), item.mimeType(), item.role()));
      }
    }
    return new ModerationDtos.OpsPostView(
        raw.postId(),
        raw.author(),
        raw.title() == null ? "" : raw.title(),
        raw.body() == null ? "" : raw.body(),
        raw.coverUrl() == null ? "" : raw.coverUrl(),
        raw.postType() == null ? "" : raw.postType(),
        raw.category() == null ? "" : raw.category(),
        raw.original(),
        raw.contentKind() == null ? "" : raw.contentKind(),
        raw.eventTitle() == null ? "" : raw.eventTitle(),
        raw.visibility() == null ? "" : raw.visibility(),
        media,
        raw.takenDown(),
        raw.publishedAt() == null ? "" : raw.publishedAt());
  }

  public ModerationDtos.OpsUser getUser(long targetId) {
    List<UserSummary> briefs = userClient.briefs(internalToken, List.of(targetId));
    UserSummary brief = briefs == null || briefs.isEmpty() ? null : briefs.get(0);
    boolean frozen = false;
    try {
      AuthClient.FreezeStatus st = authClient.freezeStatus(internalToken, targetId);
      frozen = st != null && st.frozen();
    } catch (RuntimeException ex) {
      log.warn("ops freeze-status {} failed", targetId);
    }
    if (brief == null) {
      return new ModerationDtos.OpsUser(String.valueOf(targetId), "", "", "", frozen);
    }
    return new ModerationDtos.OpsUser(brief.userId(), brief.pageId(), brief.nickname(), brief.avatarUrl(), frozen);
  }

  public List<ModerationDtos.OpsUser> searchUsers(String q) {
    if (!StringUtils.hasText(q) || q.trim().length() < 1) {
      throw BusinessException.validation("请输入一页号或昵称");
    }
    List<UserSummary> found = userClient.suggest(internalToken, q.trim());
    if (found == null || found.isEmpty()) {
      return List.of();
    }
    List<ModerationDtos.OpsUser> out = new ArrayList<>();
    for (UserSummary row : found) {
      if (row == null || !StringUtils.hasText(row.userId())) {
        continue;
      }
      boolean frozen = false;
      try {
        AuthClient.FreezeStatus st = authClient.freezeStatus(internalToken, Long.parseLong(row.userId()));
        frozen = st != null && st.frozen();
      } catch (RuntimeException ex) {
        log.warn("ops freeze-status {} failed", row.userId());
      }
      out.add(new ModerationDtos.OpsUser(row.userId(), row.pageId(), row.nickname(), row.avatarUrl(), frozen));
    }
    return out;
  }

  public ModerationDtos.OpsUser freezeUser(long actorId, long targetId, boolean frozen) {
    assertCanPunishUser(actorId, targetId);
    if (frozen) {
      authClient.freeze(internalToken, targetId);
    } else {
      authClient.unfreeze(internalToken, targetId);
    }
    List<UserSummary> briefs = userClient.briefs(internalToken, List.of(targetId));
    UserSummary brief = briefs == null || briefs.isEmpty() ? null : briefs.get(0);
    if (brief == null) {
      return new ModerationDtos.OpsUser(String.valueOf(targetId), "", "", "", frozen);
    }
    return new ModerationDtos.OpsUser(brief.userId(), brief.pageId(), brief.nickname(), brief.avatarUrl(), frozen);
  }

  private void applyAction(long handlerId, Report row, String action) {
    if ("dismiss".equals(action)) {
      return;
    }
    if ("takedown".equals(action)) {
      requirePost(row);
      contentClient.takedown(internalToken, row.getTargetId());
      return;
    }
    if ("restore".equals(action)) {
      requirePost(row);
      contentClient.restore(internalToken, row.getTargetId());
      return;
    }
    long punishId = punishUserId(row);
    assertCanPunishUser(handlerId, punishId);
    if ("ban".equals(action)) {
      if ("post".equals(row.getTargetType())) {
        contentClient.takedown(internalToken, row.getTargetId());
      }
      authClient.freeze(internalToken, punishId);
      return;
    }
    authClient.unfreeze(internalToken, punishId);
  }

  private void requirePost(Report row) {
    if (!"post".equals(row.getTargetType())) {
      throw BusinessException.validation("该动作只能用于笔记");
    }
  }

  private long punishUserId(Report row) {
    if ("user".equals(row.getTargetType())) {
      return row.getTargetId();
    }
    requirePost(row);
    List<ContentClient.PostBrief> list = contentClient.postBriefs(internalToken, List.of(row.getTargetId()));
    if (list == null || list.isEmpty() || !StringUtils.hasText(list.get(0).authorId())) {
      throw BusinessException.notFound("找不到笔记作者");
    }
    try {
      return Long.parseLong(list.get(0).authorId());
    } catch (NumberFormatException ex) {
      throw BusinessException.notFound("找不到笔记作者");
    }
  }

  private Map<Long, UserSummary> loadPeople(List<Report> rows, Map<Long, ContentClient.PostBrief> posts) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Report row : rows) {
      if (row.getReporterId() != null) {
        ids.add(row.getReporterId());
      }
      if (row.getHandlerId() != null) {
        ids.add(row.getHandlerId());
      }
      if ("user".equals(row.getTargetType()) && row.getTargetId() != null) {
        ids.add(row.getTargetId());
      }
    }
    for (ContentClient.PostBrief brief : posts.values()) {
      if (brief != null && StringUtils.hasText(brief.authorId())) {
        try {
          ids.add(Long.parseLong(brief.authorId()));
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<UserSummary> list = userClient.briefs(internalToken, List.copyOf(ids));
    if (list == null) {
      return Map.of();
    }
    return list.stream().collect(Collectors.toMap(u -> Long.parseLong(u.userId()), Function.identity(), (a, b) -> a));
  }

  private Map<Long, ContentClient.PostBrief> loadPosts(List<Report> rows) {
    List<Long> ids = new ArrayList<>();
    for (Report row : rows) {
      if ("post".equals(row.getTargetType()) && row.getTargetId() != null) {
        ids.add(row.getTargetId());
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<ContentClient.PostBrief> list = contentClient.postBriefs(internalToken, ids);
    if (list == null) {
      return Map.of();
    }
    Map<Long, ContentClient.PostBrief> map = new java.util.HashMap<>();
    for (ContentClient.PostBrief brief : list) {
      if (brief != null && StringUtils.hasText(brief.postId())) {
        map.put(Long.parseLong(brief.postId()), brief);
      }
    }
    return map;
  }

  private static ModerationDtos.OpsReport toView(
      Report row, Map<Long, UserSummary> people, Map<Long, ContentClient.PostBrief> posts) {
    String title = "";
    String pageId = "";
    String authorId = "";
    boolean takenDown = false;
    if ("post".equals(row.getTargetType())) {
      ContentClient.PostBrief brief = posts.get(row.getTargetId());
      if (brief != null) {
        title = brief.title() == null ? "" : brief.title();
        authorId = brief.authorId() == null ? "" : brief.authorId();
        takenDown = brief.takenDown();
        if (StringUtils.hasText(authorId)) {
          try {
            UserSummary author = people.get(Long.parseLong(authorId));
            if (author != null && author.pageId() != null) {
              pageId = author.pageId();
            }
          } catch (NumberFormatException ignored) {
            // skip
          }
        }
      }
    } else if ("user".equals(row.getTargetType())) {
      UserSummary target = people.get(row.getTargetId());
      if (target != null) {
        title = target.nickname() == null ? "" : target.nickname();
        pageId = target.pageId() == null ? "" : target.pageId();
      }
    }
    return new ModerationDtos.OpsReport(
        String.valueOf(row.getId()),
        row.getTargetType(),
        String.valueOf(row.getTargetId()),
        title,
        pageId,
        authorId,
        takenDown,
        row.getReasonCode(),
        row.getDetail() == null ? "" : row.getDetail(),
        row.getStatus() != null && row.getStatus() == STATUS_DONE ? "done" : "pending",
        people.get(row.getReporterId()),
        people.get(row.getHandlerId()),
        row.getHandleAction() == null ? "" : row.getHandleAction(),
        row.getHandleNote() == null ? "" : row.getHandleNote(),
        iso(row.getCreatedAt()),
        iso(row.getHandledAt()));
  }

  private void assertCanPunishUser(long actorId, long targetId) {
    if (targetId == actorId) {
      throw BusinessException.forbidden("不能处置自己");
    }
    if (staffGuard.isStaff(targetId)) {
      throw BusinessException.forbidden("不能处置运营员工");
    }
  }

  private static Integer parseStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return null;
    }
    return switch (status.trim()) {
      case "pending" -> STATUS_PENDING;
      case "done" -> STATUS_DONE;
      default -> throw BusinessException.validation("status 须为 pending 或 done");
    };
  }

  private static long parseId(String raw, String field) {
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      throw BusinessException.validation(field + " 不合法");
    }
  }

  private static String iso(LocalDateTime time) {
    if (time == null) {
      return "";
    }
    return time.atZone(ZoneId.systemDefault()).toInstant().toString();
  }

  private static String blankToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
