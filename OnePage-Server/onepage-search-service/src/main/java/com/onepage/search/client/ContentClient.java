package com.onepage.search.client;

import com.onepage.search.dto.SearchDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** 回填公开笔记。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @GetMapping("/internal/search/posts")
  SearchDtos.PostPage searchablePosts(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);
}
