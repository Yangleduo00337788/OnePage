package com.onepage.common.web;

/**
 * C 端与运营两张 Cookie。同一 Redis，按路径 / {@code audience=ops} / 请求头选哪一张。
 * 网关没有依赖 common 时，逻辑与 {@code com.onepage.gateway.SessionCookies} 保持一致。
 */
public final class SessionCookies {
  public static final String AUDIENCE_HEADER = "X-OnePage-Audience";

  private SessionCookies() {}

  public static String resolveAudience(String query, String header) {
    if (query != null && !query.isBlank()) {
      return query;
    }
    return header;
  }

  public static boolean isOps(String path, String audience) {
    if ("ops".equals(audience)) {
      return true;
    }
    return path != null && (path.equals("/api/v1/ops") || path.startsWith("/api/v1/ops/"));
  }

  public static String name(String path, String audience, String community, String ops) {
    return isOps(path, audience) ? ops : community;
  }
}
