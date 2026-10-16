package com.onepage.auth.config;

import com.onepage.common.web.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.List;

/**
 * 运营登录只读写运营 Cookie，不碰 C 端 {@code ONEPAGE_SESSION}。
 */
public class AudienceCookieSerializer implements CookieSerializer {
  private final DefaultCookieSerializer community;
  private final DefaultCookieSerializer ops;

  public AudienceCookieSerializer(String communityName, String opsName) {
    this.community = plain(communityName);
    this.ops = plain(opsName);
  }

  @Override
  public List<String> readCookieValues(HttpServletRequest request) {
    return pick(request).readCookieValues(request);
  }

  @Override
  public void writeCookieValue(CookieValue cookieValue) {
    pick(cookieValue.getRequest()).writeCookieValue(cookieValue);
  }

  private CookieSerializer pick(HttpServletRequest request) {
    String audience = SessionCookies.resolveAudience(
        request.getParameter("audience"),
        request.getHeader(SessionCookies.AUDIENCE_HEADER));
    return SessionCookies.isOps(request.getRequestURI(), audience) ? ops : community;
  }

  private static DefaultCookieSerializer plain(String name) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(name);
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    serializer.setUseSecureCookie(false);
    serializer.setUseBase64Encoding(false);
    return serializer;
  }
}
