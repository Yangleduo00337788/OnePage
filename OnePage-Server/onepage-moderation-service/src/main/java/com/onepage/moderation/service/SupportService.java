package com.onepage.moderation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.moderation.client.NotifyClient;
import com.onepage.moderation.client.UserClient;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.entity.SupportTicket;
import com.onepage.moderation.mapper.SupportTicketMapper;
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

/** 帮助反馈、主播入驻、大 V 申请。结案必填回复并通知申请人；通过后打主播/大 V 标识。 */
@Service
public class SupportService {
  private static final Logger log = LoggerFactory.getLogger(SupportService.class);
  private static final Set<String> KINDS = Set.of("feedback", "live", "verify");
  private static final int STATUS_PENDING = 0;
  private static final int STATUS_DONE = 1;
  private static final TypeReference<List<String>> URLS = new TypeReference<>() {};

  private final SupportTicketMapper mapper;
  private final UserClient userClient;
  private final NotifyClient notifyClient;
  private final ObjectMapper objectMapper;
  private final String internalToken;

  public SupportService(
      SupportTicketMapper mapper,
      UserClient userClient,
      NotifyClient notifyClient,
      ObjectMapper objectMapper,
      @Value("${onepage.internal-token}") String internalToken) {
    this.mapper = mapper;
    this.userClient = userClient;
    this.notifyClient = notifyClient;
    this.objectMapper = objectMapper;
    this.internalToken = internalToken;
  }

  public long countPending() {
    Long n = mapper.selectCount(Wrappers.<SupportTicket>lambdaQuery().eq(SupportTicket::getStatus, STATUS_PENDING));
    return n == null ? 0L : n;
  }

  public ModerationDtos.CreateSupportResult create(long userId, ModerationDtos.CreateSupportRequest body) {
    if (body == null || !StringUtils.hasText(body.kind())) {
      throw BusinessException.validation("kind 必填");
    }
    String kind = body.kind().trim();
    if (!KINDS.contains(kind)) {
      throw BusinessException.validation("kind 须为 feedback / live / verify");
    }
    String title = blank(body.title());
    String contact = blank(body.contact());
    String text = blank(body.body());
    if (title.length() > 64) {
      throw BusinessException.validation("名称过长");
    }
    if (contact.length() > 64) {
      throw BusinessException.validation("联系方式过长");
    }
    if (text.length() > 2000) {
      throw BusinessException.validation("正文过长");
    }
    if ("feedback".equals(kind) && text.isEmpty()) {
      throw BusinessException.validation("请填写反馈和意见");
    }
    if (("live".equals(kind) || "verify".equals(kind)) && (title.isEmpty() || contact.isEmpty())) {
      throw BusinessException.validation("请填写名称和联系方式");
    }
    SupportTicket row = new SupportTicket();
    row.setId(IdGenerator.nextId());
    row.setUserId(userId);
    row.setKind(kind);
    row.setTitle(title);
    row.setContact(contact);
    row.setBody(text);
    row.setMediaUrls(toJson(normalizeUrls(body.mediaUrls(), kind)));
    row.setStatus(STATUS_PENDING);
    row.setCreatedAt(LocalDateTime.now());
    mapper.insert(row);
    return new ModerationDtos.CreateSupportResult(String.valueOf(row.getId()));
  }

  public ModerationDtos.SupportPage page(String kind, String status, String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    String kindFilter = StringUtils.hasText(kind) ? kind.trim() : null;
    if (kindFilter != null && !KINDS.contains(kindFilter)) {
      throw BusinessException.validation("kind 不合法");
    }
    Integer statusCode = null;
    if ("pending".equals(status)) {
      statusCode = STATUS_PENDING;
    } else if ("done".equals(status)) {
      statusCode = STATUS_DONE;
    } else if (StringUtils.hasText(status)) {
      throw BusinessException.validation("status 须为 pending / done");
    }
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
    List<SupportTicket> rows = mapper.page(kindFilter, statusCode, cursorAt, cursorId, size + 1);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    Map<Long, UserSummary> people = loadPeople(rows);
    List<ModerationDtos.OpsSupport> items = new ArrayList<>();
    for (SupportTicket row : rows) {
      items.add(toView(row, people));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      SupportTicket last = rows.getLast();
      if (last.getCreatedAt() != null && last.getId() != null) {
        next = last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + "_" + last.getId();
      }
    }
    return new ModerationDtos.SupportPage(items, next, hasMore);
  }

  @Transactional
  public ModerationDtos.ResolveResult resolve(long handlerId, long ticketId, ModerationDtos.ResolveRequest body) {
    SupportTicket row = mapper.selectById(ticketId);
    if (row == null) {
      throw BusinessException.notFound("工单不存在");
    }
    if (Integer.valueOf(STATUS_DONE).equals(row.getStatus())) {
      throw BusinessException.validation("工单已处理");
    }
    String kind = row.getKind() == null ? "" : row.getKind();
    String action = body == null || !StringUtils.hasText(body.action()) ? "reply" : body.action().trim();
    if ("feedback".equals(kind) && !"reply".equals(action)) {
      throw BusinessException.validation("反馈结案须回复");
    }
    if (("live".equals(kind) || "verify".equals(kind)) && !"approve".equals(action) && !"reject".equals(action)) {
      throw BusinessException.validation("入驻/认证须通过或驳回");
    }
    String note = body == null ? null : blankToNull(body.note());
    if (note == null) {
      throw BusinessException.validation("请填写回复内容");
    }
    if (note.length() > 500) {
      throw BusinessException.validation("回复过长");
    }
    if ("approve".equals(action) && row.getUserId() != null) {
      try {
        if ("live".equals(kind)) {
          userClient.setBadges(internalToken, row.getUserId(), true, null, null);
        } else if ("verify".equals(kind)) {
          userClient.setBadges(internalToken, row.getUserId(), null, true, null);
        }
      } catch (RuntimeException ex) {
        log.warn("set support badges failed ticket={}", ticketId, ex);
        throw BusinessException.configMissing("开通标识失败，请稍后重试");
      }
    }
    row.setStatus(STATUS_DONE);
    row.setHandlerId(handlerId);
    row.setHandleNote(note);
    row.setHandledAt(LocalDateTime.now());
    mapper.updateById(row);
    notifyApplicant(handlerId, row, action, note);
    return new ModerationDtos.ResolveResult(String.valueOf(ticketId), action);
  }

  private void notifyApplicant(long handlerId, SupportTicket row, String action, String note) {
    if (row.getUserId() == null) {
      return;
    }
    String type = switch (row.getKind() == null ? "" : row.getKind()) {
      case "live" -> "apply_live";
      case "verify" -> "apply_verify";
      default -> "support_reply";
    };
    String preview = switch (action) {
      case "approve" -> "已通过：" + note;
      case "reject" -> "未通过：" + note;
      default -> note;
    };
    try {
      notifyClient.ingest(
          internalToken,
          new NotifyClient.IngestRequest(
              row.getUserId(),
              type,
              handlerId,
              "support:" + row.getId(),
              null,
              null,
              preview));
    } catch (RuntimeException ex) {
      log.warn("support notify failed ticket={}", row.getId(), ex);
    }
  }

  private Map<Long, UserSummary> loadPeople(List<SupportTicket> rows) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    for (SupportTicket row : rows) {
      if (row.getUserId() != null) {
        ids.add(row.getUserId());
      }
      if (row.getHandlerId() != null) {
        ids.add(row.getHandlerId());
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
      log.warn("load support users failed", ex);
      return Map.of();
    }
  }

  private ModerationDtos.OpsSupport toView(SupportTicket row, Map<Long, UserSummary> people) {
    UserSummary user = people.get(row.getUserId());
    UserSummary handler = people.get(row.getHandlerId());
    return new ModerationDtos.OpsSupport(
        String.valueOf(row.getId()),
        row.getKind(),
        row.getTitle() == null ? "" : row.getTitle(),
        row.getContact() == null ? "" : row.getContact(),
        row.getBody() == null ? "" : row.getBody(),
        parseUrls(row.getMediaUrls()),
        Integer.valueOf(STATUS_DONE).equals(row.getStatus()) ? "done" : "pending",
        user,
        handler,
        row.getHandleNote(),
        row.getCreatedAt() == null ? "" : row.getCreatedAt().toString(),
        row.getHandledAt() == null ? null : row.getHandledAt().toString());
  }

  private List<String> normalizeUrls(List<String> raw, String kind) {
    if (!"feedback".equals(kind) || raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String item : raw) {
      if (!StringUtils.hasText(item)) {
        continue;
      }
      String url = item.trim();
      if (url.length() > 512 || !(url.startsWith("https://") || url.startsWith("http://"))) {
        throw BusinessException.validation("图片地址不合法");
      }
      out.add(url);
      if (out.size() >= 6) {
        break;
      }
    }
    return out;
  }

  private String toJson(List<String> urls) {
    try {
      return objectMapper.writeValueAsString(urls);
    } catch (Exception ex) {
      return "[]";
    }
  }

  private List<String> parseUrls(String raw) {
    if (!StringUtils.hasText(raw)) {
      return List.of();
    }
    try {
      List<String> urls = objectMapper.readValue(raw, URLS);
      return urls == null ? List.of() : urls;
    } catch (Exception ex) {
      return List.of();
    }
  }

  private static String blank(String value) {
    return value == null ? "" : value.trim();
  }

  private static String blankToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
