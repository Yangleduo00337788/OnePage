package com.onepage.im.config;

import com.onepage.im.web.ImHandshakeInterceptor;
import com.onepage.im.web.ImWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 {@code /ws/im}。Origin 放行：流量应只经网关，握手仍要 {@code X-User-Id}。
 * 网关转发时 Origin 可能变成网关自己的地址，不能只写前端白名单。
 */
@Configuration
@EnableWebSocket
public class ImWebSocketConfig implements WebSocketConfigurer {
  private final ImWebSocketHandler handler;
  private final ImHandshakeInterceptor interceptor;

  public ImWebSocketConfig(ImWebSocketHandler handler, ImHandshakeInterceptor interceptor) {
    this.handler = handler;
    this.interceptor = interceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/im")
        .addInterceptors(interceptor)
        .setAllowedOriginPatterns("*");
  }
}
