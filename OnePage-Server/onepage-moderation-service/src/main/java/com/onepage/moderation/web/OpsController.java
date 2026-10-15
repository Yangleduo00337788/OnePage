package com.onepage.moderation.web;

import com.onepage.common.dto.UserSummary;
import com.onepage.common.web.CurrentUser;
import com.onepage.moderation.client.UserClient;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 运营后台。必须登录且通过 {@link StaffGuard}。 */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {
  private final ModerationService moderationService;
  private final StaffGuard staffGuard;
  private final UserClient userClient;
  private final String internalToken;

  public OpsController(
      ModerationService moderationService,
      StaffGuard staffGuard,
      UserClient userClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.moderationService = moderationService;
    this.staffGuard = staffGuard;
    this.userClient = userClient;
    this.internalToken = internalToken;
  }

  @GetMapping("/me")
  public UserSummary me(HttpServletRequest request) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    List<UserSummary> briefs = userClient.briefs(internalToken, List.of(userId));
    if (briefs == null || briefs.isEmpty()) {
      return new UserSummary(String.valueOf(userId), "", "", "");
    }
    return briefs.get(0);
  }

  @GetMapping("/reports")
  public ModerationDtos.CursorPage list(
      HttpServletRequest request,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.page(status, cursor, limit);
  }

  @PatchMapping("/reports/{reportId}")
  public ModerationDtos.ResolveResult resolve(
      HttpServletRequest request,
      @PathVariable("reportId") long reportId,
      @RequestBody ModerationDtos.ResolveRequest body) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.resolve(userId, reportId, body);
  }

  @GetMapping("/posts/{postId}")
  public ModerationDtos.OpsPostView post(
      HttpServletRequest request,
      @PathVariable("postId") long postId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.opsPost(postId);
  }

  @GetMapping("/users/{userId}")
  public ModerationDtos.OpsUser user(
      HttpServletRequest request,
      @PathVariable("userId") long targetId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.getUser(targetId);
  }

  @GetMapping("/users")
  public List<ModerationDtos.OpsUser> users(
      HttpServletRequest request,
      @RequestParam("q") String q) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.searchUsers(q);
  }

  @PostMapping("/users/{userId}/freeze")
  public ModerationDtos.OpsUser freeze(
      HttpServletRequest request,
      @PathVariable("userId") long targetId) {
    long actorId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(actorId);
    return moderationService.freezeUser(actorId, targetId, true);
  }

  @PostMapping("/users/{userId}/unfreeze")
  public ModerationDtos.OpsUser unfreeze(
      HttpServletRequest request,
      @PathVariable("userId") long targetId) {
    long actorId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(actorId);
    return moderationService.freezeUser(actorId, targetId, false);
  }
}
