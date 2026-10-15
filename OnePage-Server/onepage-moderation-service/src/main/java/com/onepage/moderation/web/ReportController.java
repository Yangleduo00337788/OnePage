package com.onepage.moderation.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 社区用户提交举报。必须登录。 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ModerationService moderationService;

  public ReportController(ModerationService moderationService) {
    this.moderationService = moderationService;
  }

  @PostMapping
  public ResponseEntity<ModerationDtos.CreateReportResult> create(
      HttpServletRequest request,
      @RequestBody ModerationDtos.CreateReportRequest body) {
    ModerationDtos.CreateReportResult result =
        moderationService.create(CurrentUser.requireUserId(request), body);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }
}
