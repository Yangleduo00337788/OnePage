package com.onepage.content.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** 拉作者摘要。name 必须等于 user 服务在 Nacos 里的应用名。 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {
  @GetMapping("/internal/profiles/{userId}/summary")
  UserSummary summary(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") Long userId);
}
