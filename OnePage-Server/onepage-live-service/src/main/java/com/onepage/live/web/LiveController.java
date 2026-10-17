package com.onepage.live.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.live.dto.LiveDtos;
import com.onepage.live.service.LiveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 开播须登录；正在直播列表公开。 */
@RestController
@RequestMapping("/api/v1/live")
public class LiveController {
  private final LiveService liveService;

  public LiveController(LiveService liveService) {
    this.liveService = liveService;
  }

  @PostMapping("/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public LiveDtos.RoomCreated create(HttpServletRequest request, @RequestBody LiveDtos.RoomSpec spec) {
    return liveService.createRoom(CurrentUser.requireUserId(request), spec);
  }

  @PostMapping("/rooms/{roomId}/start")
  public LiveDtos.LiveStart start(HttpServletRequest request, @PathVariable("roomId") long roomId) {
    return liveService.start(CurrentUser.requireUserId(request), roomId);
  }

  @PostMapping("/rooms/{roomId}/end")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void end(HttpServletRequest request, @PathVariable("roomId") long roomId) {
    liveService.end(CurrentUser.requireUserId(request), roomId);
  }

  @GetMapping("/mine")
  public ResponseEntity<LiveDtos.LiveStart> mine(HttpServletRequest request) {
    LiveDtos.LiveStart start = liveService.mine(CurrentUser.requireUserId(request));
    if (start == null) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(start);
  }

  @GetMapping("/sessions")
  public LiveDtos.LivePage sessions(
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "category", required = false) String category) {
    return liveService.list(q, category);
  }

  @GetMapping("/sessions/{sessionId}")
  public LiveDtos.LiveItem session(@PathVariable("sessionId") long sessionId) {
    return liveService.get(sessionId);
  }
}
