package com.onepage.auth.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign 客户端：声明式 HTTP。{@code name} 必须等于 user 服务在 Nacos 里的 spring.application.name。
 * 运行时由负载均衡选实例，禁止写死 {@code localhost:8082}（多实例/换端口都会挂）。
 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {

  @PostMapping("/internal/profiles")
  UserSummary createProfile(
      @RequestHeader("X-Internal-Token") String token,
      @RequestBody CreateProfileRequest body);

  @GetMapping("/internal/profiles/{userId}/summary")
  UserSummary summary(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") Long userId);

  record CreateProfileRequest(Long userId, String nickname) {}
}
