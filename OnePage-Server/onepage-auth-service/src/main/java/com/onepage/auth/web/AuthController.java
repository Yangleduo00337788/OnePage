package com.onepage.auth.web;

import com.onepage.auth.service.AuthService;
import com.onepage.auth.service.OAuthService;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * 登录注册 HTTP 入口。{@code @RestController} = Controller + 方法返回值直接当 JSON。
 * {@code @RequestMapping} 是这一组接口的公共前缀，网关按此前缀转到本服务。
 * 成功登录后 Spring Session 写 Cookie {@code ONEPAGE_SESSION}（经网关 Set-Cookie 回浏览器）。
 * {@code record XxxRequest}：请求体 DTO；{@code @Valid} 触发字段上的 {@code @NotBlank}。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService authService;
  private final OAuthService oAuthService;
  private final String frontendUrl;

  public AuthController(
      AuthService authService,
      OAuthService oAuthService,
      @Value("${onepage.frontend-url}") String frontendUrl) {
    this.authService = authService;
    this.oAuthService = oAuthService;
    this.frontendUrl = frontendUrl;
  }

  /** 发验证码。公开接口，网关 SessionAuthFilter 放行。 */
  @PostMapping("/sms/send")
  public Map<String, Integer> sendSms(@Valid @RequestBody SmsSendRequest body) {
    int cooldown = authService.sendSms(body.phone(), body.scene());
    return Map.of("cooldownSec", cooldown);
  }

  /** 短信登录：校验码 → 无账号则创建 → 写 Session。 */
  @PostMapping("/sms/login")
  public UserSummary smsLogin(
      @Valid @RequestBody SmsLoginRequest body,
      @RequestParam(value = "audience", required = false) String audience,
      HttpServletRequest request) {
    return authService.smsLogin(body.phone(), body.code(), request, opsAudience(audience));
  }

  /** 注册成功用 201 Created，和「已有账号再登录」的 200 区分。 */
  @PostMapping("/register")
  public ResponseEntity<UserSummary> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
    UserSummary summary = authService.register(body.phone(), body.email(), body.password(), body.nickname(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(summary);
  }

  @PostMapping("/login")
  public UserSummary login(
      @Valid @RequestBody LoginRequest body,
      @RequestParam(value = "audience", required = false) String audience,
      HttpServletRequest request) {
    return authService.passwordLogin(body.account(), body.password(), request, opsAudience(audience));
  }

  /** 实际清 Session 在网关完成；这里保留给直连本端口时用。 */
  @PostMapping("/logout")
  public Map<String, Boolean> logout(HttpServletRequest request) {
    authService.logout(request);
    return Map.of("ok", true);
  }

  @GetMapping("/account")
  public AuthService.AccountView account(HttpServletRequest request) {
    return authService.account(CurrentUser.requireUserId(request));
  }

  @PostMapping("/password")
  public AuthService.AccountView setPassword(HttpServletRequest request, @Valid @RequestBody SetPasswordRequest body) {
    return authService.setPassword(CurrentUser.requireUserId(request), body.oldPassword(), body.password());
  }

  @PostMapping("/password/reset")
  public UserSummary resetPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
    return authService.resetPassword(body.phone(), body.code(), body.password(), request);
  }

  /**
   * 302 跳到 GitHub/Google 授权页。{@code @PathVariable("provider")} 必须写名字：
   * Boot 3 默认可能拿不到参数名，不写会 500。
   */
  @GetMapping("/oauth/{provider}/authorize")
  public ResponseEntity<Void> oauthAuthorize(@PathVariable("provider") String provider) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(oAuthService.authorizeUrl(provider)))
        .build();
  }

  /** 第三方带着 code 回调这里：换用户信息、建号、写 Session，再 302 回前端首页。 */
  @GetMapping("/oauth/{provider}/callback")
  public ResponseEntity<Void> oauthCallback(
      @PathVariable("provider") String provider,
      @RequestParam("code") String code,
      HttpServletRequest request) {
    OAuthService.OAuthProfile profile = oAuthService.exchange(provider, code);
    authService.oauthLogin(provider, profile.providerUserId(), profile.nickname(), request);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(frontendUrl + "/"))
        .build();
  }

  private static boolean opsAudience(String audience) {
    return "ops".equals(audience);
  }

  public record SmsSendRequest(@NotBlank String phone, @NotBlank String scene) {}
  public record SmsLoginRequest(@NotBlank String phone, @NotBlank String code) {}
  public record RegisterRequest(String phone, String email, @NotBlank String password, String nickname) {}
  public record LoginRequest(@NotBlank String account, @NotBlank String password) {}
  public record SetPasswordRequest(String oldPassword, @NotBlank String password) {}
  public record ResetPasswordRequest(@NotBlank String phone, @NotBlank String code, @NotBlank String password) {}
}
