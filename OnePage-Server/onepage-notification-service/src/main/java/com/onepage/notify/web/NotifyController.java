package com.onepage.notify.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.notify.dto.NotifyDtos;
import com.onepage.notify.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人通知。必须登录。{@code group} 对应社区三 Tab：comment / reaction / follow。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotifyController {
  private final NotifyService notifyService;

  public NotifyController(NotifyService notifyService) {
    this.notifyService = notifyService;
  }

  @GetMapping
  public NotifyDtos.CursorPage list(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
      @RequestParam(value = "group", required = false) String group) {
    return notifyService.page(CurrentUser.requireUserId(request), group, cursor, limit, unreadOnly);
  }

  @PostMapping("/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(HttpServletRequest request, @RequestBody NotifyDtos.ReadRequest body) {
    notifyService.markRead(CurrentUser.requireUserId(request), body);
  }

  @GetMapping("/unread-count")
  public NotifyDtos.UnreadCount unread(HttpServletRequest request) {
    return notifyService.unread(CurrentUser.requireUserId(request));
  }
}
