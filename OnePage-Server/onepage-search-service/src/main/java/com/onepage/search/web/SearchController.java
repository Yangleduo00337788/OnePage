package com.onepage.search.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.search.dto.SearchDtos;
import com.onepage.search.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 全局搜索。公开 GET；登录后「关注 / 已看 / 未看」才生效。 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {
  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/search")
  public SearchDtos.SearchResult search(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "type", required = false) String type,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "postType", required = false) String postType,
      @RequestParam(value = "sort", required = false) String sort,
      @RequestParam(value = "time", required = false) String time,
      @RequestParam(value = "scope", required = false) String scope) {
    return searchService.search(
        q, type, cursor, limit, postType, sort, time, scope, CurrentUser.tryUserId(request));
  }
}
