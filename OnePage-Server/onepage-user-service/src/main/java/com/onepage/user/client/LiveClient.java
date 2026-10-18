package com.onepage.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** 改资料前问 live-service 是否正在开播。直播服务未启动时由调用方吞异常。 */
@FeignClient(name = "onepage-live-service")
public interface LiveClient {
  @GetMapping("/internal/live/users/{userId}/active")
  ActiveView active(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId);

  record ActiveView(boolean active) {}
}
