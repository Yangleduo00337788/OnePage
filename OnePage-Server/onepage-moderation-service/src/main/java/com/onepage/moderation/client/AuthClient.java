package com.onepage.moderation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 冻结账号：改 auth 库 status。 */
@FeignClient(name = "onepage-auth-service")
public interface AuthClient {
  @PostMapping("/internal/accounts/{userId}/freeze")
  void freeze(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId);
}
