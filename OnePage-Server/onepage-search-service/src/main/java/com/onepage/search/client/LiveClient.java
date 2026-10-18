package com.onepage.search.client;

import com.onepage.search.dto.SearchDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 回填正在直播场次。 */
@FeignClient(name = "onepage-live-service")
public interface LiveClient {
  @GetMapping("/internal/live/search")
  SearchDtos.LivePage searchableLives(@RequestHeader("X-Internal-Token") String token);
}
