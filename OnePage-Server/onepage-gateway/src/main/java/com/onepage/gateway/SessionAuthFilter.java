package com.onepage.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 登录态过滤器。{@code GlobalFilter}：对所有路由生效，不必在 YAML 里逐条挂。
 * {@code @Component}：让 Spring 扫描并注册这个 Filter。
 * <ul>
   *   <li>公开接口（登录/注册/OAuth/短信、以及 GET Feed/笔记/评论/他人主页与其笔记列表）直接放行。</li>
 *   <li>其余请求必须能从 Redis Session 读到 {@code userId}，否则 401。</li>
 *   <li>通过后写入 {@code X-User-Id}，下游只信这个头。公开 GET 若已登录也会写入，便于 likedByMe。</li>
 * </ul>
 * Session 由 auth 登录成功时写入 Redis；C 端与运营 Cookie 名必须与 auth 一致。
 */
@Component
public class SessionAuthFilter implements GlobalFilter, Ordered {

  private final ObjectMapper objectMapper;
  private final String communityCookie;
  private final String opsCookie;

  public SessionAuthFilter(
      ObjectMapper objectMapper,
      @Value("${onepage.session-cookie-name}") String communityCookie,
      @Value("${onepage.ops-session-cookie-name}") String opsCookie) {
    this.objectMapper = objectMapper;
    this.communityCookie = communityCookie;
    this.opsCookie = opsCookie;
  }

  /**
   * {@code ServerWebExchange}：一次请求+响应的上下文（WebFlux 没有 HttpServletRequest）。
   * {@code chain.filter}：放行到下一个过滤器，最后才转发到下游服务。
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    HttpMethod method = exchange.getRequest().getMethod();
    String cookieName = cookieName(exchange);
    if (HttpMethod.OPTIONS.equals(method) || isPublic(path, method)) {
      if (exchange.getRequest().getCookies().getFirst(cookieName) == null) {
        return chain.filter(exchange);
      }
      return exchange.getSession().flatMap(session -> {
        Object userId = session.getAttribute("userId");
        if (userId == null || userId.toString().isBlank()) {
          return chain.filter(exchange);
        }
        var mutated = exchange.getRequest().mutate()
            .header("X-User-Id", userId.toString())
            .build();
        return chain.filter(exchange.mutate().request(mutated).build());
      });
    }
    return exchange.getSession().flatMap(session -> {
      Object userId = session.getAttribute("userId");
      if (userId == null || userId.toString().isBlank()) {
        return json(exchange.getResponse(), HttpStatus.UNAUTHORIZED, Map.of(
            "code", "UNAUTHORIZED",
            "message", "未登录",
            "requestId", ""));
      }
      if (HttpMethod.POST.equals(exchange.getRequest().getMethod())
          && "/api/v1/auth/logout".equals(path)) {
        // 登出只在网关 invalidate。若再转发到 auth 也 invalidate，两边抢同一份 Redis WebSession，请求会挂住。
        return session.invalidate().then(json(exchange.getResponse(), HttpStatus.OK, Map.of("ok", true)));
      }
      var mutated = exchange.getRequest().mutate()
          .header("X-User-Id", userId.toString())
          .build();
      return chain.filter(exchange.mutate().request(mutated).build());
    });
  }

  private String cookieName(ServerWebExchange exchange) {
    String path = exchange.getRequest().getURI().getPath();
    String audience = SessionCookies.resolveAudience(
        exchange.getRequest().getQueryParams().getFirst("audience"),
        exchange.getRequest().getHeaders().getFirst(SessionCookies.AUDIENCE_HEADER));
    return SessionCookies.name(path, audience, communityCookie, opsCookie);
  }

  /** 登录/注册/发码/找回密码/OAuth 公开；GET 发现流、笔记详情、评论列表公开。 */
  private boolean isPublic(String path, HttpMethod method) {
    if (path.equals("/api/v1/auth/sms/send")
        || path.equals("/api/v1/auth/sms/login")
        || path.equals("/api/v1/auth/register")
        || path.equals("/api/v1/auth/login")
        || path.equals("/api/v1/auth/password/reset")
        || path.startsWith("/api/v1/auth/oauth/")) {
      return true;
    }
    if (!HttpMethod.GET.equals(method)) {
      return false;
    }
    if (path.startsWith("/api/v1/feeds/")
        || path.equals("/api/v1/catalog")
        || path.startsWith("/api/v1/catalog/")) {
      return true;
    }
    if ("/api/v1/posts/mine".equals(path) || "/api/v1/users/me".equals(path) || path.startsWith("/api/v1/users/me/")) {
      return false;
    }
    return path.matches("/api/v1/posts/[^/]+")
        || path.matches("/api/v1/posts/[^/]+/comments")
        || path.matches("/api/v1/users/[^/]+")
        || path.matches("/api/v1/users/[^/]+/posts")
        || path.matches("/api/v1/users/[^/]+/following")
        || path.matches("/api/v1/users/[^/]+/followers");
  }

  private Mono<Void> json(ServerHttpResponse response, HttpStatus status, Map<String, ?> body) {
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      bytes = "{\"code\":\"ERROR\"}".getBytes(StandardCharsets.UTF_8);
    }
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  /** {@link Ordered#getOrder()} 越小越先执行。-1 保证在路由转发之前拦住未登录请求。 */
  @Override
  public int getOrder() {
    return -1;
  }
}
