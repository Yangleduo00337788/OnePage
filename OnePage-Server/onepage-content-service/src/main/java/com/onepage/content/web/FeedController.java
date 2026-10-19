package com.onepage.content.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.FeedService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 发现流公开；关注流须登录，未登录由前端拦到登录。 */
@RestController
@RequestMapping("/api/v1/feeds")
public class FeedController {
  private final FeedService feedService;

  public FeedController(FeedService feedService) {
    this.feedService = feedService;
  }

  @GetMapping("/explore")
  public FeedDtos.CursorPage explore(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "category", required = false) String category) {
    return feedService.page(CurrentUser.tryUserId(request), cursor, limit, category);
  }

  @GetMapping("/following")
  public FeedDtos.CursorPage following(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return feedService.pageFollowing(CurrentUser.tryUserId(request), cursor, limit);
  }
}
