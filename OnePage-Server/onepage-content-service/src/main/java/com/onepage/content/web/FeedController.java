package com.onepage.content.web;

import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.FeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** following 与 explore 在 P1 同一数据：还没有关注图。 */
@RestController
@RequestMapping("/api/v1/feeds")
public class FeedController {
  private final FeedService feedService;

  public FeedController(FeedService feedService) {
    this.feedService = feedService;
  }

  @GetMapping("/explore")
  public FeedDtos.CursorPage explore(
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "category", required = false) String category) {
    return feedService.page(cursor, limit, category);
  }

  @GetMapping("/following")
  public FeedDtos.CursorPage following(
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return feedService.page(cursor, limit, null);
  }
}
