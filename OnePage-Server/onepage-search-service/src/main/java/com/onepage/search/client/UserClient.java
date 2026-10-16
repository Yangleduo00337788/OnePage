package com.onepage.search.client;

import com.onepage.search.dto.SearchDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 回填用户资料；筛选「关注」时取关注列表。 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {
  @GetMapping("/internal/search/users")
  SearchDtos.UserPage searchableUsers(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @GetMapping("/internal/users/{userId}/followee-ids")
  List<Long> followeeIds(
      @RequestHeader("X-Internal-Token") String token, @PathVariable("userId") long userId);
}
