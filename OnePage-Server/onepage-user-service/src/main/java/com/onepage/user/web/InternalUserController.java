package com.onepage.user.web;

import com.onepage.common.dto.UserSummary;
import com.onepage.user.dto.CountView;
import com.onepage.user.dto.CreatorFanStats;
import com.onepage.user.dto.UserCursorPage;
import com.onepage.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
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

  @GetMapping("/users/{userId}/followee-ids")
  public List<Long> followeeIds(HttpServletRequest request, @PathVariable("userId") long userId) {
    tokenFilter.assertInternal(request);
    return userService.listFolloweeIds(userId);
  }

  @GetMapping("/users/{userId}/creator-fan-stats")
  public CreatorFanStats creatorFanStats(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam("from") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime from,
      @RequestParam("to") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime to,
      @RequestParam("prevFrom") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime prevFrom,
      @RequestParam("prevTo") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime prevTo) {
    tokenFilter.assertInternal(request);
    return userService.creatorFanStats(userId, from, to, prevFrom, prevTo);
  }

  @GetMapping("/users/{userId}/count-new-followers")
  public CountView countNewFollowers(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam("from") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime from,
      @RequestParam("to") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime to) {
    tokenFilter.assertInternal(request);
    return new CountView(userService.countNewFollowers(userId, from, to));
  }

  @PostMapping("/users/{userId}/count-in-followers")
  public CountView countInFollowers(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestBody List<Long> ids) {
    tokenFilter.assertInternal(request);
    return new CountView(userService.countFollowersIn(userId, ids));
  }

  @GetMapping("/users/briefs")
  public List<UserSummary> briefs(
      HttpServletRequest request,
      @RequestParam("ids") List<Long> ids) {
    tokenFilter.assertInternal(request);
    return userService.briefs(ids);
  }

  @GetMapping("/users/by-keys")
  public List<UserSummary> byKeys(
      HttpServletRequest request,
      @RequestParam("keys") List<String> keys) {
    tokenFilter.assertInternal(request);
    return userService.byKeys(keys);
  }

  @GetMapping("/users/suggest")
  public List<UserSummary> suggest(
      HttpServletRequest request,
      @RequestParam("q") String q) {
    tokenFilter.assertInternal(request);
    return userService.suggest(q, null);
  }

  @GetMapping("/search/users")
  public UserService.SearchUserPage searchable(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    tokenFilter.assertInternal(request);
    return userService.listSearchable(cursor, limit);
  }

  @GetMapping("/users/count")
  public Count count(HttpServletRequest request) {
    tokenFilter.assertInternal(request);
    return new Count(userService.countProfiles());
  }

  @PostMapping("/users/clear-object-key")
  public void clearObjectKey(
      HttpServletRequest request, @RequestParam("objectKey") String objectKey) {
    tokenFilter.assertInternal(request);
    userService.clearObjectKey(objectKey);
  }

  @GetMapping("/users/{userId}/ops/following")
  public UserCursorPage opsFollowing(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    tokenFilter.assertInternal(request);
    return userService.opsRelations(userId, true, cursor, limit);
  }

  @GetMapping("/users/{userId}/ops/followers")
  public UserCursorPage opsFollowers(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    tokenFilter.assertInternal(request);
    return userService.opsRelations(userId, false, cursor, limit);
  }

  @PostMapping("/users/{userId}/badges")
  public void setBadges(
      HttpServletRequest request,
      @PathVariable("userId") long userId,
      @RequestParam(value = "liveHost", required = false) Boolean liveHost,
      @RequestParam(value = "verified", required = false) Boolean verified) {
    tokenFilter.assertInternal(request);
    userService.setBadges(userId, liveHost, verified);
  }

  @PostMapping("/users/{followerId}/following/{followeeId}/remove")
  public void opsRemoveFollow(
      HttpServletRequest request,
      @PathVariable("followerId") long followerId,
      @PathVariable("followeeId") long followeeId) {
    tokenFilter.assertInternal(request);
    userService.opsRemoveFollow(followerId, followeeId);
  }

  @GetMapping("/users/ops")
  public UserService.UserSummaryPage opsUsers(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    tokenFilter.assertInternal(request);
    return userService.opsPage(q, cursor, limit);
  }

  public record Count(long users) {}

  public record CreateProfileRequest(Long userId, String nickname) {}
}
