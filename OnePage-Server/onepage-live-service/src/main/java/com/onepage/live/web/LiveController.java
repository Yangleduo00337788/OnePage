package com.onepage.live.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.live.dto.LiveDtos;
import com.onepage.live.service.LiveChatService;
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
  private final LiveChatService chatService;

  public LiveController(LiveService liveService, LiveChatService chatService) {
    this.liveService = liveService;
    this.chatService = chatService;
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

  @PostMapping("/rooms/{roomId}/notice")
  public LiveDtos.NoticeView notice(
      HttpServletRequest request,
      @PathVariable("roomId") long roomId,
      @RequestBody LiveDtos.NoticeSpec spec) {
    return liveService.updateNotice(CurrentUser.requireUserId(request), roomId, spec);
  }

  @PostMapping("/sessions/{sessionId}/share/link")
  public LiveDtos.ShareLinkResult shareLink(
      HttpServletRequest request, @PathVariable("sessionId") long sessionId) {
    return liveService.shareLink(CurrentUser.requireUserId(request), sessionId);
  }

  @PostMapping("/sessions/{sessionId}/share/im")
  public ResponseEntity<LiveDtos.ShareImResult> shareIm(
      HttpServletRequest request,
      @PathVariable("sessionId") long sessionId,
      @RequestBody LiveDtos.ShareImRequest body) {
    String messageId = liveService.shareIm(
        CurrentUser.requireUserId(request),
        sessionId,
        body == null ? null : body.conversationId(),
        body == null ? null : body.targetUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(new LiveDtos.ShareImResult(messageId));
  }

  @GetMapping("/stats")
  public LiveDtos.CreatorStats stats(
      HttpServletRequest request, @RequestParam(value = "range", required = false) String range) {
    return liveService.creatorStats(CurrentUser.requireUserId(request), range);
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
  public LiveDtos.LiveItem session(
      @PathVariable("sessionId") long sessionId,
      @RequestParam(value = "peek", required = false) Boolean peek) {
    return liveService.get(sessionId, Boolean.TRUE.equals(peek));
  }

  @GetMapping("/sessions/{sessionId}/messages")
  public LiveDtos.ChatPage messages(
      @PathVariable("sessionId") long sessionId,
      @RequestParam(value = "after", required = false) String after) {
    return chatService.list(sessionId, after);
  }

  @PostMapping("/sessions/{sessionId}/heartbeat")
  public LiveDtos.PresenceTick heartbeat(
      HttpServletRequest request,
      @PathVariable("sessionId") long sessionId,
      @RequestBody(required = false) LiveDtos.HeartbeatSpec spec) {
    return liveService.heartbeat(CurrentUser.tryUserId(request), sessionId, spec);
  }

  @PostMapping("/sessions/{sessionId}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public LiveDtos.ChatLine say(
      HttpServletRequest request,
      @PathVariable("sessionId") long sessionId,
      @RequestBody LiveDtos.ChatSpec spec) {
    return chatService.send(CurrentUser.requireUserId(request), sessionId, spec);
  }
}
