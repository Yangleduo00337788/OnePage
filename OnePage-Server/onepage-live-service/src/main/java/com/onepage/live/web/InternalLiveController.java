package com.onepage.live.web;

import com.onepage.live.dto.LiveDtos;
import com.onepage.live.service.LiveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 内网：是否正在直播、MediaMTX 推流钩子。浏览器不能调。 */
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

  @PostMapping("/hooks/publish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void publish(
      HttpServletRequest request,
      @RequestParam(value = "path", required = false) String path,
      @RequestParam(value = "streamKey", required = false) String streamKey,
      @RequestBody(required = false) LiveDtos.HookSpec body) {
    tokenFilter.assertInternal(request);
    liveService.onPublish(hookPath(path, body), hookKey(streamKey, body));
  }

  @PostMapping("/hooks/unpublish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unpublish(
      HttpServletRequest request,
      @RequestParam(value = "path", required = false) String path,
      @RequestParam(value = "streamKey", required = false) String streamKey,
      @RequestBody(required = false) LiveDtos.HookSpec body) {
    tokenFilter.assertInternal(request);
    liveService.onUnpublish(hookPath(path, body), hookKey(streamKey, body));
  }

  public record ActiveView(boolean active) {}

  private static String hookPath(String path, LiveDtos.HookSpec body) {
    if (path != null && !path.isBlank()) {
      return path;
    }
    return body == null ? null : body.path();
  }

  private static String hookKey(String streamKey, LiveDtos.HookSpec body) {
    if (streamKey != null && !streamKey.isBlank()) {
      return streamKey;
    }
    return body == null ? null : body.streamKey();
  }
}
