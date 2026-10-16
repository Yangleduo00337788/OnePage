package com.onepage.search.web;

import com.onepage.search.dto.SearchDtos;
import com.onepage.search.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 全局搜索。公开 GET，登录与否都能查公开笔记和用户。 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {
  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/search")
  public SearchDtos.SearchResult search(
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "type", required = false) String type,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "postType", required = false) String postType) {
    return searchService.search(q, type, cursor, limit, postType);
  }
}
