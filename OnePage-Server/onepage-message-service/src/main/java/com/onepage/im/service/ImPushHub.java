package com.onepage.im.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.im.dto.ImDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 本机连接表 + Redis 频道。多实例时对端可能在另一进程，不能只写内存 Map。
 */
@Component
public class ImPushHub implements MessageListener {
  public static final String CHANNEL = "onepage:im:push";
  private static final Logger log = LoggerFactory.getLogger(ImPushHub.class);

  private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> local =
      new ConcurrentHashMap<>();
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public ImPushHub(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public void attach(long userId, WebSocketSession session) {
    local.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(session);
  }

  public void detach(long userId, WebSocketSession session) {
    CopyOnWriteArraySet<WebSocketSession> set = local.get(userId);
    if (set == null) {
      return;
    }
    set.remove(session);
    if (set.isEmpty()) {
      local.remove(userId, set);
    }
  }

  public void publish(long userId, String conversationId, ImDtos.MessageItem payload) {
    try {
      String json = objectMapper.writeValueAsString(
          new ImDtos.PushFrame(String.valueOf(userId), conversationId, payload));
      redis.convertAndSend(CHANNEL, json);
    } catch (Exception ex) {
      log.warn("im push publish failed user {}", userId, ex);
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      ImDtos.PushFrame frame = objectMapper.readValue(message.getBody(), ImDtos.PushFrame.class);
      if (frame == null || frame.userId() == null || frame.payload() == null) {
        return;
      }
      long userId = Long.parseLong(frame.userId());
      String json = objectMapper.writeValueAsString(Map.of(
          "op", "message",
          "conversationId", frame.conversationId() == null ? "" : frame.conversationId(),
          "payload", frame.payload()));
      deliver(userId, json);
    } catch (Exception ex) {
      log.warn("im push consume failed", ex);
    }
  }

  public void sendTo(WebSocketSession session, Map<String, ?> body) {
    try {
      write(session, objectMapper.writeValueAsString(body));
    } catch (Exception ex) {
      log.warn("im ws write failed session {}", session.getId(), ex);
    }
  }

  private void deliver(long userId, String json) {
    CopyOnWriteArraySet<WebSocketSession> set = local.get(userId);
    if (set == null || set.isEmpty()) {
      return;
    }
    for (WebSocketSession session : set) {
      write(session, json);
    }
  }

  private void write(WebSocketSession session, String json) {
    if (session == null || !session.isOpen()) {
      return;
    }
    synchronized (session) {
      try {
        session.sendMessage(new TextMessage(json));
      } catch (IOException ex) {
        log.warn("im ws send failed session {}", session.getId(), ex);
      }
    }
  }
}
