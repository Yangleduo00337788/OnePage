package com.onepage.gateway;

import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.session.WebSessionIdResolver;

import java.time.Duration;
import java.util.List;

/**
 * 按请求选 C 端或运营 Cookie，避免运营登录覆盖社区 Session。
 */
public class DualCookieWebSessionIdResolver implements WebSessionIdResolver {
  private final String communityCookie;
  private final String opsCookie;

  public DualCookieWebSessionIdResolver(String communityCookie, String opsCookie) {
    this.communityCookie = communityCookie;
    this.opsCookie = opsCookie;
  }

  @Override
  public List<String> resolveSessionIds(ServerWebExchange exchange) {
    String name = cookieName(exchange);
    HttpCookie cookie = exchange.getRequest().getCookies().getFirst(name);
    if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
      return List.of();
    }
    return List.of(cookie.getValue());
  }

  @Override
  public void setSessionId(ServerWebExchange exchange, String sessionId) {
    exchange.getResponse().addCookie(cookie(cookieName(exchange), sessionId, null));
  }

  @Override
  public void expireSession(ServerWebExchange exchange) {
    exchange.getResponse().addCookie(cookie(cookieName(exchange), "", Duration.ZERO));
  }

  private String cookieName(ServerWebExchange exchange) {
    String path = exchange.getRequest().getURI().getPath();
    String audience = SessionCookies.resolveAudience(
        exchange.getRequest().getQueryParams().getFirst("audience"),
        exchange.getRequest().getHeaders().getFirst(SessionCookies.AUDIENCE_HEADER));
    return SessionCookies.name(path, audience, communityCookie, opsCookie);
  }

  private static ResponseCookie cookie(String name, String value, Duration maxAge) {
    var builder = ResponseCookie.from(name, value)
        .path("/")
        .httpOnly(true)
        .sameSite("Lax")
        .secure(false);
    if (maxAge != null) {
      builder.maxAge(maxAge);
    }
    return builder.build();
  }
}
