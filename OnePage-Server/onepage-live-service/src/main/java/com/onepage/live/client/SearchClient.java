package com.onepage.live.client;

import com.onepage.live.dto.LiveDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 正在直播写入搜索索引。搜索服务未启动时由调用方吞异常。 */
@FeignClient(name = "onepage-search-service")
public interface SearchClient {
  @PostMapping("/internal/index/lives")
  void upsertLive(@RequestHeader("X-Internal-Token") String token, @RequestBody LiveDtos.LiveItem body);

  @DeleteMapping("/internal/index/lives/{sessionId}")
  void deleteLive(
      @RequestHeader("X-Internal-Token") String token, @PathVariable("sessionId") String sessionId);
}
