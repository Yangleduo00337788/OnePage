package com.onepage.notification.web;

import com.onepage.notification.dto.NotifyDtos;
import com.onepage.notification.service.NotifyService;
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

/** 仅服务间投递。路径不以 {@code /api/v1} 开头。 */
@RestController
@RequestMapping("/internal")
public class InternalNotifyController {
  private final NotifyService notifyService;
  private final InternalTokenFilter tokenFilter;

  public InternalNotifyController(NotifyService notifyService, InternalTokenFilter tokenFilter) {
    this.notifyService = notifyService;
    this.tokenFilter = tokenFilter;
  }

  @PostMapping("/notifications")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ingest(HttpServletRequest request, @RequestBody NotifyDtos.IngestRequest body) {
    tokenFilter.assertInternal(request);
    notifyService.ingest(body);
  }

  @GetMapping("/notifications/ops")
  public NotifyDtos.CursorPage opsPage(
      HttpServletRequest request,
      @RequestParam("userId") long userId,
      @RequestParam(value = "group", required = false) String group,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    tokenFilter.assertInternal(request);
    return notifyService.page(userId, group, cursor, limit, false);
  }

  @PostMapping("/notifications/{id}/hide")
  public ResponseEntity<Void> hide(HttpServletRequest request, @PathVariable("id") long id) {
    tokenFilter.assertInternal(request);
    notifyService.opsHide(id);
    return ResponseEntity.noContent().build();
  }
}
