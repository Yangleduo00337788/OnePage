package com.onepage.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/** 社区规则。治理服务未启动时由调用方回落到默认值。 */
@FeignClient(name = "onepage-moderation-service")
public interface ModerationClient {
  @GetMapping("/internal/policy")
  PolicyView policy(@RequestHeader("X-Internal-Token") String token);

  record PolicyView(
      List<String> bannedWords, int nicknameMax, int bioMax, int imageMaxMb, int videoMaxMb) {}
}
