package com.onepage.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录态过滤器。{@code GlobalFilter}：对所有路由生效，不必在 YAML 里逐条挂。
 * {@code @Component}：让 Spring 扫描并注册这个 Filter。
 * <ul>
   *   <li>公开接口（登录/注册/OAuth/短信、以及 GET Feed/笔记/评论/他人主页与其笔记列表、直播列表/详情/聊天/心跳）直接放行。</li>
 *   <li>其余请求必须能从 Redis Session 读到 {@code userId}，否则 401。</li>
 *   <li>通过后写入 {@code X-User-Id}，下游只信这个头。公开 GET 若已登录也会写入，便于 likedByMe。</li>
 * </ul>
 * Session 由 auth 登录成功时写入 Redis；C 端与运营 Cookie 名必须与 auth 一致。
 */
@Component
public class SessionAuthFilter implements GlobalFilter, Ordered {

  /** 同一会话在这段时间内不再打 Redis。千并发复用一个 Cookie 时，每次读会话会把评论和发笔记打成未登录。 */
  private static final long USER_CACHE_TTL_MILLIS = 15_000L;
  private static final Logger log = LoggerFactory.getLogger(SessionAuthFilter.class);

  private final ObjectMapper objectMapper;
  private final String communityCookie;
  private final String opsCookie;
  private final ConcurrentHashMap<String, CachedUser> usersBySession = new ConcurrentHashMap<>();

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
    if (HttpMethod.POST.equals(method) && "/api/v1/auth/logout".equals(path)) {
      forgetSession(exchange, cookieName);
      // 登出只在网关 invalidate。若再转发到 auth 也 invalidate，两边抢同一份 Redis WebSession，请求会挂住。
      return exchange.getSession()
          .flatMap(session -> session.invalidate())
          .then(json(exchange.getResponse(), HttpStatus.OK, Map.of("ok", true)))
          .onErrorResume(ex -> unauthorized(exchange));
    }
    if (HttpMethod.OPTIONS.equals(method) || isPublic(path, method)) {
      if (exchange.getRequest().getCookies().getFirst(cookieName) == null) {
        return chain.filter(exchange);
      }
      return resolveUserId(exchange, cookieName)
          .onErrorResume(ex -> {
            log.warn("公开接口读会话失败，按未登录继续: {}", ex.getClass().getSimpleName());
            return Mono.empty();
          })
          .map(Optional::of)
          .defaultIfEmpty(Optional.empty())
          .flatMap(user -> {
            if (user.isEmpty() || user.get().isBlank()) {
              return chain.filter(exchange);
            }
            return continueWithUser(exchange, chain, user.get());
          });
    }
    return resolveUserId(exchange, cookieName)
        .onErrorResume(ex -> {
          log.warn("受保护接口读会话失败: {}", ex.getClass().getSimpleName());
          return Mono.empty();
        })
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(user -> {
          if (user.isEmpty() || user.get().isBlank()) {
            return unauthorized(exchange);
          }
          return continueWithUser(exchange, chain, user.get());
        });
  }

  /**
   * 缓存命中则不再读 Redis。过期或读失败时，仍用上一份用户，避免把已登录的写请求打成未登录。
   * 空 Mono 表示这个 Cookie 还没有解析出用户。
   */
  private Mono<String> resolveUserId(ServerWebExchange exchange, String cookieName) {
    String sessionId = sessionId(exchange, cookieName);
    CachedUser cached = sessionId == null ? null : usersBySession.get(sessionId);
    long now = System.currentTimeMillis();
    if (cached != null && cached.expiresAtMillis > now) {
      return Mono.just(cached.userId);
    }
    return exchange.getSession().flatMap(session -> {
      Object raw = session.getAttribute("userId");
      if (raw == null || raw.toString().isBlank()) {
        if (cached != null) {
          return Mono.just(cached.userId);
        }
        return Mono.empty();
      }
      String userId = raw.toString();
      if (sessionId != null) {
        usersBySession.put(sessionId, new CachedUser(userId, now + USER_CACHE_TTL_MILLIS));
      }
      return Mono.just(userId);
    }).onErrorResume(ex -> {
      if (cached != null) {
        return Mono.just(cached.userId);
      }
      return Mono.error(ex);
    });
  }

  private void forgetSession(ServerWebExchange exchange, String cookieName) {
    String sessionId = sessionId(exchange, cookieName);
    if (sessionId != null) {
      usersBySession.remove(sessionId);
    }
  }

  private static String sessionId(ServerWebExchange exchange, String cookieName) {
    HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
    if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
      return null;
    }
    return cookie.getValue();
  }

  private Mono<Void> continueWithUser(ServerWebExchange exchange, GatewayFilterChain chain, String userId) {
    var mutated = exchange.getRequest().mutate()
        .header("X-User-Id", userId)
        .build();
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange) {
    return json(exchange.getResponse(), HttpStatus.UNAUTHORIZED, Map.of(
        "code", "UNAUTHORIZED",
        "message", "未登录",
        "requestId", ""));
  }

  private record CachedUser(String userId, long expiresAtMillis) {}

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
    if (HttpMethod.POST.equals(method)
        && path.matches("/api/v1/live/sessions/[^/]+/heartbeat")) {
      return true;
    }
    if (!HttpMethod.GET.equals(method)) {
      return false;
    }
    if (path.startsWith("/api/v1/feeds/")
        || path.equals("/api/v1/catalog")
        || path.startsWith("/api/v1/catalog/")
        || path.equals("/api/v1/search")
        || path.startsWith("/api/v1/search/")
        || path.equals("/api/v1/ai/bootstrap")
        || path.equals("/api/v1/live/sessions")
        || path.matches("/api/v1/live/sessions/[^/]+")
        || path.matches("/api/v1/live/sessions/[^/]+/messages")) {
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
