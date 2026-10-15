package com.onepage.moderation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 冻结/解冻：改 auth 库 status。 */
@FeignClient(name = "onepage-auth-service")
public interface AuthClient {
  @PostMapping("/internal/accounts/{userId}/freeze")
  void freeze(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId);

  @PostMapping("/internal/accounts/{userId}/unfreeze")
  void unfreeze(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId);

  @GetMapping("/internal/accounts/{userId}/freeze-status")
  FreezeStatus freezeStatus(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId);

  record FreezeStatus(String userId, boolean frozen) {}
}
