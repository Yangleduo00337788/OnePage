package com.onepage.moderation.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 社区用户提交帮助反馈或入驻/认证申请。必须登录。 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {
  private final SupportService supportService;

  public SupportController(SupportService supportService) {
    this.supportService = supportService;
  }

  @PostMapping
  public ResponseEntity<ModerationDtos.CreateSupportResult> create(
      HttpServletRequest request,
      @RequestBody ModerationDtos.CreateSupportRequest body) {
    ModerationDtos.CreateSupportResult result =
        supportService.create(CurrentUser.requireUserId(request), body);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }
}
