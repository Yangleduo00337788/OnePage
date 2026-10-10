package com.onepage.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 网关横切配置。{@code @Configuration} + {@code @Bean}：方法返回值交给 Spring 容器管理，
 * 别处用构造器注入就能拿到，不必自己 {@code new}。
 */
@Configuration
public class GatewayConfig {

  /**
   * 浏览器跨域。文档站 5173、社区 5174、创作者 5175 与 API 8080 不同源，必须 CORS。
   * {@code setAllowCredentials(true)} 才能带 Cookie；此时不能写 {@code *} 源，必须白名单。
   * {@code @Value} 从 yml 占位符读取，值来自环境变量 {@code CORS_ALLOWED_ORIGINS}。
   */
  @Bean
  public CorsWebFilter corsWebFilter(
      @Value("${onepage.cors-allowed-origins}") String origins) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowCredentials(true);
    List<String> originList = Arrays.stream(origins.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
    cors.setAllowedOrigins(originList);
    cors.addAllowedHeader(CorsConfiguration.ALL);
    cors.setAllowedMethods(List.of(
        HttpMethod.GET.name(),
        HttpMethod.POST.name(),
        HttpMethod.PUT.name(),
        HttpMethod.PATCH.name(),
        HttpMethod.DELETE.name(),
        HttpMethod.OPTIONS.name()));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return new CorsWebFilter(source);
  }

  /**
   * Redis 令牌桶限流。replenish = 每秒补几个令牌，burst = 桶容量。
   * YAML 路由里 {@code key-resolver: "#{@clientIpKeyResolver}"} 引用下面那个 Bean 名。
   */
  @Bean
  public RedisRateLimiter redisRateLimiter(
      @Value("${onepage.rate-replenish}") int replenish,
      @Value("${onepage.rate-burst}") int burst) {
    return new RedisRateLimiter(replenish, burst);
  }

  /**
   * WebFlux 读/写 Session Cookie。必须关 Secure（本地 http）、名称为 ONEPAGE_SESSION。
   * SameSite=Lax：同站导航带 Cookie，跨站 POST 不带，减少 CSRF。
   * HttpOnly：JS 读不到 Cookie，降低 XSS 偷 Session 的风险。
   */
  @Bean
  public WebSessionIdResolver webSessionIdResolver() {
    CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
    resolver.setCookieName("ONEPAGE_SESSION");
    resolver.addCookieInitializer(builder -> builder.path("/")
        .httpOnly(true)
        .sameSite("Lax")
        .secure(false));
    return resolver;
  }

  /**
   * 限流键：优先 {@code X-Forwarded-For} 第一个 IP（反代后面真实客户端），否则用连接远端地址。
   * {@code Mono.just}：WebFlux 里一切都是异步流，即使立刻有值也要包一层。
   */
  @Bean
  public KeyResolver clientIpKeyResolver() {
    return exchange -> {
      String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return Mono.just(forwarded.split(",")[0].trim());
      }
      var addr = exchange.getRequest().getRemoteAddress();
      return Mono.just(addr == null ? "unknown" : addr.getAddress().getHostAddress());
    };
  }
}
