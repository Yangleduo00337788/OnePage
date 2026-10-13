package com.onepage.user.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.user.dto.PostCursorPage;
import com.onepage.user.dto.UserCursorPage;
import com.onepage.user.dto.UserProfileView;
import com.onepage.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对浏览器的资料接口。{@code /me} 一律 {@link CurrentUser#requireUserId}，不要自己读 Session。
 * {@code GET /{pageId}} 与 {@code GET /{pageId}/posts} 公开；已登录时带 {@code X-User-Id} 才能看到自己的仅自己可见笔记。
 * {@code GET /me/favorites} {@code /likes} {@code /history} 仅本人。
 * {@code POST/DELETE /{pageId}/follow} 必须登录；关注/粉丝列表公开。
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
  public UserProfileView byPageId(HttpServletRequest request, @PathVariable("pageId") String pageId) {
    return userService.byPageId(pageId, CurrentUser.tryUserId(request));
  }

  @GetMapping("/{pageId}/posts")
  public PostCursorPage posts(
      HttpServletRequest request,
      @PathVariable("pageId") String pageId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return userService.postsByPageId(pageId, CurrentUser.tryUserId(request), cursor, limit);
  }

  @GetMapping("/me/favorites")
  public PostCursorPage favorites(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return userService.myFavorites(CurrentUser.requireUserId(request), cursor, limit);
  }

  @GetMapping("/me/likes")
  public PostCursorPage likes(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return userService.myLikes(CurrentUser.requireUserId(request), cursor, limit);
  }

  @GetMapping("/me/history")
  public PostCursorPage history(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return userService.myHistory(CurrentUser.requireUserId(request), cursor, limit);
  }

  @GetMapping("/{pageId}/following")
  public UserCursorPage following(
      HttpServletRequest request,
      @PathVariable("pageId") String pageId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return userService.following(pageId, CurrentUser.tryUserId(request), cursor, limit);
  }

  @GetMapping("/{pageId}/followers")
  public UserCursorPage followers(
      HttpServletRequest request,
      @PathVariable("pageId") String pageId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return userService.followers(pageId, CurrentUser.tryUserId(request), cursor, limit);
  }

  @PostMapping("/{pageId}/follow")
  public ResponseEntity<FollowResult> follow(HttpServletRequest request, @PathVariable("pageId") String pageId) {
    boolean created = userService.follow(CurrentUser.requireUserId(request), pageId);
    return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(new FollowResult(true));
  }

  @DeleteMapping("/{pageId}/follow")
  public ResponseEntity<Void> unfollow(HttpServletRequest request, @PathVariable("pageId") String pageId) {
    userService.unfollow(CurrentUser.requireUserId(request), pageId);
    return ResponseEntity.noContent().build();
  }

  public record FollowResult(boolean followed) {}

  public record PatchMeRequest(
      String nickname,
      String bio,
      Integer gender,
      String avatarMediaId,
      String coverMediaId
  ) {}
}
