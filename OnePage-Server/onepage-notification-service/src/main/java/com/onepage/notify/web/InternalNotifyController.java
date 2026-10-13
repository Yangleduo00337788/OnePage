package com.onepage.notify.web;

import com.onepage.notify.dto.NotifyDtos;
import com.onepage.notify.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
