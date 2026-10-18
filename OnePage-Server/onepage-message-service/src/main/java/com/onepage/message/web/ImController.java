package com.onepage.message.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.message.dto.ImDtos;
import com.onepage.message.service.ImService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人私信。必须登录。{@code GET /unread-count} 写在 {@code /{id}} 前面，避免被当成会话 id。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ImController {
  private final ImService imService;

  public ImController(ImService imService) {
    this.imService = imService;
  }

  @GetMapping
  public ImDtos.ConversationList list(HttpServletRequest request) {
    return imService.list(CurrentUser.requireUserId(request));
  }

  @GetMapping("/unread-count")
  public ImDtos.UnreadCount unread(HttpServletRequest request) {
    return imService.unread(CurrentUser.requireUserId(request));
  }

  @PostMapping
  public ResponseEntity<ImDtos.ConversationCreated> open(
      HttpServletRequest request, @RequestBody ImDtos.CreateRequest body) {
    ImService.OpenResult result = imService.open(
        CurrentUser.requireUserId(request), body == null ? null : body.peerUserId());
    return ResponseEntity
        .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(new ImDtos.ConversationCreated(result.conversationId()));
  }

  @GetMapping("/{conversationId}/messages")
  public ImDtos.MessageList messages(
      HttpServletRequest request,
      @PathVariable("conversationId") long conversationId,
      @RequestParam(value = "beforeId", required = false) String beforeId,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return imService.messages(CurrentUser.requireUserId(request), conversationId, beforeId, limit);
  }

  @PostMapping("/{conversationId}/messages")
  public ResponseEntity<ImDtos.MessageItem> send(
      HttpServletRequest request,
      @PathVariable("conversationId") long conversationId,
      @RequestBody ImDtos.SendRequest body) {
    ImDtos.MessageItem item = imService.send(CurrentUser.requireUserId(request), conversationId, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(item);
  }
}
