package com.onepage.message.web;

import com.onepage.common.web.GatewayHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手只认网关 {@code X-User-Id}，不解析 Cookie。
 * 没有合法 id 就拒绝升级，避免直连 8085 伪造连接。
 */
@Component
public class ImHandshakeInterceptor implements HandshakeInterceptor {
  public static final String ATTR_USER_ID = "userId";

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String raw = request.getHeaders().getFirst(GatewayHeaders.USER_ID);
    if (raw == null || raw.isBlank()) {
      return false;
    }
    try {
      attributes.put(ATTR_USER_ID, Long.parseLong(raw.trim()));
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // 身份已在 beforeHandshake 写入 attributes
  }
}
