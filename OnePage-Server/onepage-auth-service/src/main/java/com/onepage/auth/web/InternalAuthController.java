package com.onepage.auth.web;

import com.onepage.auth.service.AuthService;
import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅服务间冻结账号。路径不以 {@code /api/v1} 开头。 */
@RestController
@RequestMapping("/internal")
public class InternalAuthController {
  private final AuthService authService;
  private final String expectedToken;

  public InternalAuthController(
      AuthService authService,
      @Value("${onepage.internal-token}") String expectedToken) {
    this.authService = authService;
    this.expectedToken = expectedToken;
  }

  @PostMapping("/accounts/{userId}/freeze")
  public void freeze(HttpServletRequest request, @PathVariable("userId") long userId) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
    authService.freeze(userId);
  }

  @PostMapping("/accounts/{userId}/unfreeze")
  public void unfreeze(HttpServletRequest request, @PathVariable("userId") long userId) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
    authService.unfreeze(userId);
  }

  @GetMapping("/accounts/{userId}/freeze-status")
  public AuthService.FreezeView freezeStatus(HttpServletRequest request, @PathVariable("userId") long userId) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
    return authService.freezeStatus(userId);
  }
}
