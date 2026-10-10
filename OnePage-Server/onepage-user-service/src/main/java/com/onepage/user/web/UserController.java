package com.onepage.user.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.user.dto.UserProfileView;
import com.onepage.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对浏览器的资料接口。当前用户一律 {@link CurrentUser#requireUserId}，不要自己读 Session。
 * {@code PATCH /me}：局部更新，body 里没传的字段保持原值。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public UserProfileView me(HttpServletRequest request) {
    return userService.me(CurrentUser.requireUserId(request));
  }

  @PatchMapping("/me")
  public UserProfileView patch(HttpServletRequest request, @RequestBody PatchMeRequest body) {
    return userService.updateMe(
        CurrentUser.requireUserId(request),
        body.nickname(),
        body.bio(),
        body.gender(),
        body.avatarMediaId(),
        body.coverMediaId());
  }

  /** 公开主页：用对外 pageId，不用内部数字 userId。 */
  @GetMapping("/{pageId}")
  public UserProfileView byPageId(@PathVariable("pageId") String pageId) {
    return userService.byPageId(pageId);
  }

  public record PatchMeRequest(
      String nickname,
      String bio,
      Integer gender,
      String avatarMediaId,
      String coverMediaId
  ) {}
}
