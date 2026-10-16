package com.onepage.gateway;

/**
 * 与 common {@code SessionCookies} 相同：运营路径或 {@code audience=ops} 用运营 Cookie。
 * 网关不引 common（避免 Servlet Web 进 WebFlux）。
 */
final class SessionCookies {
  static final String AUDIENCE_HEADER = "X-OnePage-Audience";

  private SessionCookies() {}

  static String resolveAudience(String query, String header) {
    if (query != null && !query.isBlank()) {
      return query;
    }
    return header;
  }

  static boolean isOps(String path, String audience) {
    if ("ops".equals(audience)) {
      return true;
    }
    return path != null && (path.equals("/api/v1/ops") || path.startsWith("/api/v1/ops/"));
  }

  static String name(String path, String audience, String community, String ops) {
    return isOps(path, audience) ? ops : community;
  }
}
