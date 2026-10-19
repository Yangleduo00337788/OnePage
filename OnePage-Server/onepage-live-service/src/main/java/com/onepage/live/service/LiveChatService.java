package com.onepage.live.service;

import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.live.client.UserClient;
import com.onepage.live.dto.LiveDtos;
import com.onepage.live.dto.LiveHit;
import com.onepage.live.entity.LiveChat;
import com.onepage.live.mapper.LiveChatMapper;
import com.onepage.live.mapper.LiveSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 直播间文字聊天。不写私信库。 */
@Service
public class LiveChatService {
  private static final Logger log = LoggerFactory.getLogger(LiveChatService.class);
  private static final int BODY_MAX = 200;
  private static final int PAGE_MAX = 50;
  private static final String KIND_TEXT = "text";
  private static final String KIND_JOIN = "join";
  private static final String JOIN_BODY = "来了";
  private final LiveChatMapper chatMapper;
  private final LiveSessionMapper sessionMapper;
  private final UserClient userClient;
  private final String internalToken;

  public LiveChatService(
      LiveChatMapper chatMapper,
      LiveSessionMapper sessionMapper,
      UserClient userClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.chatMapper = chatMapper;
    this.sessionMapper = sessionMapper;
    this.userClient = userClient;
    this.internalToken = internalToken;
  }

  public LiveDtos.ChatPage list(long sessionId, String afterId) {
    requireSession(sessionId);
    List<LiveChat> rows;
    if (StringUtils.hasText(afterId)) {
      long after;
      try {
        after = Long.parseLong(afterId.trim());
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("消息游标无效");
      }
      rows = chatMapper.after(sessionId, after, PAGE_MAX);
    } else {
      List<LiveChat> latest = chatMapper.latest(sessionId, PAGE_MAX);
      rows = new ArrayList<>(latest);
      Collections.reverse(rows);
    }
    return new LiveDtos.ChatPage(toLines(rows));
  }

  public LiveDtos.ChatLine send(long userId, long sessionId, LiveDtos.ChatSpec spec) {
    LiveHit hit = requireSession(sessionId);
    if (!"live".equals(hit.getStatus())) {
      throw BusinessException.conflict("直播已结束");
    }
    String body = spec == null || spec.body() == null ? "" : spec.body().trim();
    if (body.isEmpty() || body.length() > BODY_MAX) {
      throw BusinessException.validation("请输入 1～200 字");
    }
    LiveChat row = new LiveChat();
    row.setId(IdGenerator.nextId());
    row.setSessionId(sessionId);
    row.setUserId(userId);
    row.setBody(body);
    row.setMsgType(KIND_TEXT);
    row.setCreatedAt(LocalDateTime.now());
    chatMapper.insert(row);
    List<LiveDtos.ChatLine> lines = toLines(List.of(row));
    return lines.getFirst();
  }

  public LiveDtos.ChatLine announceJoin(long userId, long sessionId) {
    LiveChat row = new LiveChat();
    row.setId(IdGenerator.nextId());
    row.setSessionId(sessionId);
    row.setUserId(userId);
    row.setBody(JOIN_BODY);
    row.setMsgType(KIND_JOIN);
    row.setCreatedAt(LocalDateTime.now());
    chatMapper.insert(row);
    return toLines(List.of(row)).getFirst();
  }

  private LiveHit requireSession(long sessionId) {
    LiveHit hit = sessionMapper.findHit(sessionId);
    if (hit == null) {
      throw BusinessException.notFound("场次不存在");
    }
    return hit;
  }

  private List<LiveDtos.ChatLine> toLines(List<LiveChat> rows) {
    List<Long> ids = new ArrayList<>();
    for (LiveChat row : rows) {
      if (row.getUserId() != null) {
        ids.add(row.getUserId());
      }
    }
    Map<Long, UserSummary> authors = loadAuthors(ids);
    List<LiveDtos.ChatLine> out = new ArrayList<>();
    for (LiveChat row : rows) {
      UserSummary author = authors.getOrDefault(
          row.getUserId(),
          new UserSummary(String.valueOf(row.getUserId()), "", "用户", "", false, false, false));
      String at = row.getCreatedAt() == null
          ? ""
          : row.getCreatedAt().toInstant(ZoneOffset.UTC).toString();
      String kind = StringUtils.hasText(row.getMsgType()) ? row.getMsgType() : KIND_TEXT;
      out.add(new LiveDtos.ChatLine(
          String.valueOf(row.getId()),
          kind,
          row.getBody() == null ? "" : row.getBody(),
          at,
          author));
    }
    return out;
  }

  private Map<Long, UserSummary> loadAuthors(List<Long> ids) {
    Map<Long, UserSummary> map = new HashMap<>();
    if (ids.isEmpty()) {
      return map;
    }
    try {
      List<UserSummary> rows = userClient.briefs(internalToken, ids);
      if (rows == null) {
        return map;
      }
      for (UserSummary row : rows) {
        if (row != null && StringUtils.hasText(row.userId())) {
          map.put(Long.parseLong(row.userId()), row);
        }
      }
    } catch (RuntimeException ex) {
      log.warn("load live chat authors failed", ex);
    }
    return map;
  }
}
