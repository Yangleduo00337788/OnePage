package com.onepage.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.client.RestClient;

/**
 * 认证侧基础设施 Bean。
 * BCrypt：单向哈希密码，库里不存明文；{@code matches} 用来校验登录。
 * RestClient：OAuth 调 GitHub/Google 用，和 Feign（调自己人）分开。
 */
@Configuration
public class AuthConfig {
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public RestClient restClient() {
    return RestClient.create();
  }

  /**
   * Servlet Session 默认把 Cookie 值 Base64。网关 WebFlux 按原文去 Redis 查，对不上就会 401。
   * 两边都用明文 SessionId，Cookie 名与网关一致。
   */
  @Bean
  public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("ONEPAGE_SESSION");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    serializer.setUseSecureCookie(false);
    serializer.setUseBase64Encoding(false);
    return serializer;
  }
}
