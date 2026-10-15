package com.onepage.moderation.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 填工单举报人/对象摘要，以及运营搜人。 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {
  @GetMapping("/internal/users/briefs")
  List<UserSummary> briefs(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("ids") List<Long> ids);

  @GetMapping("/internal/users/suggest")
  List<UserSummary> suggest(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("q") String q);
}
