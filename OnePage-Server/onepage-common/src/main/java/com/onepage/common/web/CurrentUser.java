package com.onepage.common.web;

import com.onepage.common.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从当前 HTTP 请求取出登录用户 id。
 * <p>
 * 只读网关写入的 {@code X-User-Id}，不要 {@code request.getSession()} 或解析 Cookie：
 * user/content 是独立进程，Session 存在 Redis 且由网关/auth 管理，这里读不到同一套 Cookie 语义。
 */
public final class CurrentUser {
  private CurrentUser() {}

  /** 没有合法数字 id 就当未登录。网关本应拦掉，这里再防一手直连端口。 */
  public static long requireUserId(HttpServletRequest request) {
    Long userId = tryUserId(request);
    if (userId == null) {
      throw BusinessException.unauthorized("未登录");
    }
    return userId;
  }

  /** 公开接口用：有登录头就带上，没有就返回 null，不要 401。 */
  public static Long tryUserId(HttpServletRequest request) {
    String raw = request.getHeader(GatewayHeaders.USER_ID);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
