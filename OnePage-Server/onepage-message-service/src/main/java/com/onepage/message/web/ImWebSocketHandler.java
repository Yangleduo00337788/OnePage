package com.onepage.message.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.api.BusinessException;
import com.onepage.message.dto.ImDtos;
import com.onepage.message.service.ImPushHub;
import com.onepage.message.service.ImService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * {@code WS /ws/im}。落库仍走 {@link ImService}；本处理器只做心跳、发文本和错误帧。
 */
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(ImWebSocketHandler.class);

  private final ImPushHub hub;
  private final ImService imService;
  private final ObjectMapper objectMapper;

  public ImWebSocketHandler(ImPushHub hub, ImService imService, ObjectMapper objectMapper) {
    this.hub = hub;
    this.imService = imService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Long userId = userId(session);
    if (userId == null) {
      closeQuietly(session);
      return;
    }
    hub.attach(userId, session);
    hub.sendTo(session, Map.of("op", "connected"));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Long userId = userId(session);
    if (userId != null) {
      hub.detach(userId, session);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    Long userId = userId(session);
    if (userId == null) {
      closeQuietly(session);
      return;
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(message.getPayload());
    } catch (Exception ex) {
      hub.sendTo(session, error("VALIDATION_ERROR", "消息不是 JSON"));
      return;
    }
    String op = text(root, "op");
    if ("ping".equals(op)) {
      hub.sendTo(session, Map.of("op", "pong"));
      return;
    }
    if (!"send".equals(op)) {
      hub.sendTo(session, error("VALIDATION_ERROR", "不支持的 op"));
      return;
    }
    try {
      ImDtos.SendRequest body = new ImDtos.SendRequest(
          text(root, "msgType"),
          root.get("content"));
      long conversationId = Long.parseLong(text(root, "conversationId"));
      ImDtos.MessageItem item = imService.send(userId, conversationId, body);
      hub.sendTo(session, Map.of(
          "op", "ack",
          "conversationId", String.valueOf(conversationId),
          "payload", item));
    } catch (BusinessException ex) {
      hub.sendTo(session, error(ex.getCode(), ex.getMessage()));
    } catch (NumberFormatException ex) {
      hub.sendTo(session, error("VALIDATION_ERROR", "conversationId 不合法"));
    } catch (RuntimeException ex) {
      log.warn("im ws send failed", ex);
      hub.sendTo(session, error("ERROR", "发送失败"));
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    Long userId = userId(session);
    if (userId != null) {
      hub.detach(userId, session);
    }
    closeQuietly(session);
  }

  private static Long userId(WebSocketSession session) {
    Object raw = session.getAttributes().get(ImHandshakeInterceptor.ATTR_USER_ID);
    return raw instanceof Long id ? id : null;
  }

  private static Map<String, String> error(String code, String detail) {
    return Map.of("op", "error", "code", code == null ? "ERROR" : code, "detail", detail == null ? "" : detail);
  }

  private static String text(JsonNode root, String field) {
    if (root == null) {
      return "";
    }
    JsonNode node = root.get(field);
    if (node == null || node.isNull()) {
      return "";
    }
    if (node.isTextual() || node.isNumber()) {
      return node.asText();
    }
    return "";
  }

  private static void closeQuietly(WebSocketSession session) {
    try {
      session.close(CloseStatus.NOT_ACCEPTABLE);
    } catch (Exception ignored) {
      // 已经关掉就不再抛
    }
  }
}
