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
  private static final Set<String> ACTIONS = Set.of("dismiss", "takedown", "ban");
  private static final int STATUS_PENDING = 0;
  private static final int STATUS_DONE = 1;

  private final ReportMapper mapper;
  private final UserClient userClient;
  private final ContentClient contentClient;
  private final AuthClient authClient;
  private final String internalToken;

  public ModerationService(
      ReportMapper mapper,
      UserClient userClient,
      ContentClient contentClient,
      AuthClient authClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.mapper = mapper;
    this.userClient = userClient;
    this.contentClient = contentClient;
    this.authClient = authClient;
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
    if (reason.length() > 32) {
      throw BusinessException.validation("reasonCode 过长");
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
    Map<Long, UserSummary> reporters = loadReporters(rows);
    List<ModerationDtos.OpsReport> items = new ArrayList<>();
    for (Report row : rows) {
      items.add(toView(row, reporters.get(row.getReporterId())));
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
      throw BusinessException.validation("action 须为 dismiss / takedown / ban");
    }
    String note = blankToNull(body.note());
    if (note != null && note.length() > 500) {
      throw BusinessException.validation("备注过长");
    }
    Report row = mapper.selectById(reportId);
    if (row == null) {
      throw BusinessException.notFound("工单不存在");
    }
    if (row.getStatus() != null && row.getStatus() == STATUS_DONE) {
      throw BusinessException.conflict("工单已处理");
    }
    if ("takedown".equals(action)) {
      if (!"post".equals(row.getTargetType())) {
        throw BusinessException.validation("下架只能用于笔记");
      }
      contentClient.takedown(internalToken, row.getTargetId());
    } else if ("ban".equals(action)) {
      if (!"user".equals(row.getTargetType())) {
        throw BusinessException.validation("封禁只能用于用户");
      }
      authClient.freeze(internalToken, row.getTargetId());
    }
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

  private Map<Long, UserSummary> loadReporters(List<Report> rows) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Report row : rows) {
      if (row.getReporterId() != null) {
        ids.add(row.getReporterId());
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

  private static ModerationDtos.OpsReport toView(Report row, UserSummary reporter) {
    return new ModerationDtos.OpsReport(
        String.valueOf(row.getId()),
        row.getTargetType(),
        String.valueOf(row.getTargetId()),
        row.getReasonCode(),
        row.getDetail() == null ? "" : row.getDetail(),
        row.getStatus() != null && row.getStatus() == STATUS_DONE ? "done" : "pending",
        reporter,
        iso(row.getCreatedAt()));
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
