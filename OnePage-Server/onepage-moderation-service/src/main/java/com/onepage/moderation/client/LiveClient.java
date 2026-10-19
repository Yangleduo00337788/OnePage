package com.onepage.moderation.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 直播工单：预览场次、关播。改 live 库，不在 moderation 改场次表。 */
@FeignClient(name = "onepage-live-service")
public interface LiveClient {
  @GetMapping("/internal/live/sessions/{sessionId}")
  LiveItem session(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("sessionId") long sessionId);

  @GetMapping("/internal/live/sessions/briefs")
  List<LiveItem> briefs(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("ids") List<Long> ids);

  @PostMapping("/internal/live/sessions/{sessionId}/end")
  void end(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("sessionId") long sessionId);

  record LiveItem(
      String sessionId,
      String roomId,
      String title,
      String category,
      String status,
      String coverUrl,
      String notice,
      String playUrlHls,
      long viewerCount,
      UserSummary owner,
      String startedAt) {}
}
