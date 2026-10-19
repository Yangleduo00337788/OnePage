package com.onepage.live.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 填直播列表里的主播摘要。 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {
  @GetMapping("/internal/users/briefs")
  List<UserSummary> briefs(
      @RequestHeader("X-Internal-Token") String token, @RequestParam("ids") List<Long> ids);

  @GetMapping("/internal/users/{userId}/count-new-followers")
  CountView countNewFollowers(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId,
      @RequestParam("from") String from,
      @RequestParam("to") String to);

  record CountView(long count) {}
}
