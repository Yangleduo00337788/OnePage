package com.onepage.user.web;

import com.onepage.common.dto.UserSummary;
import com.onepage.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅服务间调用。路径不以 {@code /api/v1} 开头，网关默认不把公网流量转到这里；
 * 仍校验 token，防止有人直连 8082。
 */
@RestController
@RequestMapping("/internal")
public class InternalUserController {
  private final UserService userService;
  private final InternalTokenFilter tokenFilter;

  public InternalUserController(UserService userService, InternalTokenFilter tokenFilter) {
    this.userService = userService;
    this.tokenFilter = tokenFilter;
  }

  @PostMapping("/profiles")
  public UserSummary create(HttpServletRequest request, @RequestBody CreateProfileRequest body) {
    tokenFilter.assertInternal(request);
    return userService.createProfile(body.userId(), body.nickname());
  }

  @GetMapping("/profiles/{userId}/summary")
  public UserSummary summary(HttpServletRequest request, @PathVariable("userId") Long userId) {
    tokenFilter.assertInternal(request);
    return userService.summary(userId);
  }

  public record CreateProfileRequest(Long userId, String nickname) {}
}
