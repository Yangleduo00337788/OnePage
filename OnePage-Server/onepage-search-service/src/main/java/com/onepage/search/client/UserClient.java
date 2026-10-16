package com.onepage.search.client;

import com.onepage.search.dto.SearchDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** 回填用户资料。 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {
  @GetMapping("/internal/search/users")
  SearchDtos.UserPage searchableUsers(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);
}
