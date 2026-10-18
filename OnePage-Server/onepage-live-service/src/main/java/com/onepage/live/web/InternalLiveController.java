package com.onepage.live.web;

import com.onepage.live.service.LiveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 内网：是否正在直播。浏览器不能调。 */
@RestController
@RequestMapping("/internal/live")
public class InternalLiveController {
  private final LiveService liveService;
  private final InternalTokenFilter tokenFilter;

  public InternalLiveController(LiveService liveService, InternalTokenFilter tokenFilter) {
    this.liveService = liveService;
    this.tokenFilter = tokenFilter;
  }

  @GetMapping("/users/{userId}/active")
  public ActiveView active(HttpServletRequest request, @PathVariable("userId") long userId) {
    tokenFilter.assertInternal(request);
    return new ActiveView(liveService.hasLive(userId));
  }

  public record ActiveView(boolean active) {}
}
