package com.onepage.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 令牌桶在转发之前扣，不往响应里写 {@code X-RateLimit-*}。
 * YAML 自带的 RequestRateLimiter 会在下游已经提交后再补头，把连接掐掉。
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

  /** 路由转发之后、真正打下游之前。 */
  private static final int ORDER = 50_000;

  private final RedisRateLimiter limiter;
  private final KeyResolver keyResolver;
  private final ObjectMapper objectMapper;

  public GatewayRateLimitFilter(
      RedisRateLimiter limiter, KeyResolver clientIpKeyResolver, ObjectMapper objectMapper) {
    this.limiter = limiter;
    this.keyResolver = clientIpKeyResolver;
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    String routeId = route == null ? "gateway" : route.getId();
    return keyResolver.resolve(exchange).defaultIfEmpty("unknown").flatMap(key ->
        limiter.isAllowed(routeId, key).flatMap(decision -> {
          if (!decision.isAllowed()) {
            return tooMany(exchange);
          }
          return chain.filter(exchange);
        }));
  }

  private Mono<Void> tooMany(ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(Map.of(
          "code", "RATE_LIMITED",
          "message", "请求过于频繁",
          "requestId", ""));
    } catch (JsonProcessingException ex) {
      bytes = "{\"code\":\"RATE_LIMITED\"}".getBytes(StandardCharsets.UTF_8);
    }
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
