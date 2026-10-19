package com.onepage.content.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.content.dto.CreatorStatsDtos;
import com.onepage.content.service.CreatorStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/creator")
public class CreatorStatsController {
  private final CreatorStatsService creatorStatsService;

  public CreatorStatsController(CreatorStatsService creatorStatsService) {
    this.creatorStatsService = creatorStatsService;
  }

  @GetMapping("/stats")
  public CreatorStatsDtos.Stats stats(
      HttpServletRequest request, @RequestParam(value = "range", required = false) String range) {
    return creatorStatsService.load(CurrentUser.requireUserId(request), range);
  }
}
