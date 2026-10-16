package com.onepage.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.web.http.CookieSerializer;
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
   * Session Hash 只认 JSON。Bean 名必须是这个，auth 与网关各注册一份。
   */
  @Bean
  public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
    return new GenericJackson2JsonRedisSerializer();
  }

  /**
   * Servlet Session 默认把 Cookie 值 Base64。网关 WebFlux 按原文去 Redis 查，对不上就会 401。
   * C 端与运营两张 Cookie、同一 Redis；运营登录不读写社区 Cookie。
   */
  @Bean
  public CookieSerializer cookieSerializer(
      @Value("${onepage.session-cookie-name}") String communityCookie,
      @Value("${onepage.ops-session-cookie-name}") String opsCookie) {
    return new AudienceCookieSerializer(communityCookie, opsCookie);
  }
}
