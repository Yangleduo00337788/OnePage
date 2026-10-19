package com.onepage.moderation.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 填工单举报人/对象摘要，以及运营搜人。 */
@FeignClient(name = "onepage-user-service")
public interface UserClient {
  @GetMapping("/internal/users/briefs")
  List<UserSummary> briefs(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("ids") List<Long> ids);

  @GetMapping("/internal/users/suggest")
  List<UserSummary> suggest(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("q") String q);

  @GetMapping("/internal/users/count")
  Count count(@RequestHeader("X-Internal-Token") String token);

  @GetMapping("/internal/users/ops")
  UserPage opsUsers(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @GetMapping("/internal/users/{userId}/ops/following")
  PeerPage following(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @GetMapping("/internal/users/{userId}/ops/followers")
  PeerPage followers(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @PostMapping("/internal/users/{userId}/badges")
  void setBadges(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("userId") long userId,
      @RequestParam(value = "liveHost", required = false) Boolean liveHost,
      @RequestParam(value = "verified", required = false) Boolean verified,
      @RequestParam(value = "official", required = false) Boolean official);

  @PostMapping("/internal/users/{followerId}/following/{followeeId}/remove")
  void removeFollow(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("followerId") long followerId,
      @PathVariable("followeeId") long followeeId);

  record Count(long users) {}

  record UserPage(List<UserSummary> items, String nextCursor, boolean hasMore) {}

  record PeerPage(List<UserSummary> items, String nextCursor, boolean hasMore) {}
}
