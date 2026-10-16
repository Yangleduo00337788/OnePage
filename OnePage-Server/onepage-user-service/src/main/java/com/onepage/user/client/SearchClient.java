package com.onepage.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 用户资料写入搜索索引。搜索服务未启动时由调用方吞异常。 */
@FeignClient(name = "onepage-search-service")
public interface SearchClient {
  @PostMapping("/internal/index/users")
  void upsertUser(@RequestHeader("X-Internal-Token") String token, @RequestBody UserIndexDoc body);

  record UserIndexDoc(String userId, String pageId, String nickname, String avatarUrl, String bio) {}
}
