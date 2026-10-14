package com.onepage.content.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 拉作者摘要与关注的人。name 必须等于 user 服务在 Nacos 里的应用名。 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {
  @GetMapping("/internal/profiles/{userId}/summary")
  UserSummary summary(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") Long userId);

  @GetMapping("/internal/users/{userId}/followee-ids")
  List<Long> followeeIds(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId);

  @GetMapping("/internal/users/by-keys")
  List<UserSummary> byKeys(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("keys") List<String> keys);
}
