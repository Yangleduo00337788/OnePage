package com.onepage.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 给每个请求补 {@code X-Request-Id}：请求头带给下游，响应头回给浏览器。
 * 对照网关日志、业务 JSON 里的 requestId 时用同一串。已有该头则沿用（方便前端或压测自己传）。
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

  public static final String HEADER = "X-Request-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String existing = exchange.getRequest().getHeaders().getFirst(HEADER);
    String id = (existing == null || existing.isBlank())
        ? UUID.randomUUID().toString().replace("-", "")
        : existing;
    ServerWebExchange mutated = exchange.mutate()
        .request(builder -> builder.header(HEADER, id))
        .build();
    mutated.getResponse().getHeaders().set(HEADER, id);
    return chain.filter(mutated);
  }

  /** 比 SessionAuthFilter（-1）更早，保证 401 响应也能带上 id。 */
  @Override
  public int getOrder() {
    return -2;
  }
}
