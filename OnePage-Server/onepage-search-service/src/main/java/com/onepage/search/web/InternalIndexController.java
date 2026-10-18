package com.onepage.search.web;

import com.onepage.search.dto.SearchDtos;
import com.onepage.search.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** content/user/live 事务提交后写入或删除索引。 */
@RestController
@RequestMapping("/internal/index")
public class InternalIndexController {
  private final SearchService searchService;
  private final InternalTokenFilter tokenFilter;

  public InternalIndexController(SearchService searchService, InternalTokenFilter tokenFilter) {
    this.searchService = searchService;
    this.tokenFilter = tokenFilter;
  }

  @PostMapping("/posts")
  public void upsertPost(HttpServletRequest request, @RequestBody SearchDtos.PostBrief body) {
    tokenFilter.assertInternal(request);
    searchService.upsertPost(body);
  }

  @DeleteMapping("/posts/{postId}")
  public void deletePost(HttpServletRequest request, @PathVariable("postId") String postId) {
    tokenFilter.assertInternal(request);
    searchService.deletePost(postId);
  }

  @PostMapping("/users")
  public void upsertUser(HttpServletRequest request, @RequestBody SearchDtos.UserDoc body) {
    tokenFilter.assertInternal(request);
    searchService.upsertUser(body);
  }

  @PostMapping("/topics")
  public void upsertTopic(HttpServletRequest request, @RequestBody SearchDtos.TopicDoc body) {
    tokenFilter.assertInternal(request);
    searchService.upsertTopic(body);
  }

  @DeleteMapping("/topics/{topicId}")
  public void deleteTopic(HttpServletRequest request, @PathVariable("topicId") String topicId) {
    tokenFilter.assertInternal(request);
    searchService.deleteTopic(topicId);
  }

  @PostMapping("/lives")
  public void upsertLive(HttpServletRequest request, @RequestBody SearchDtos.LiveDoc body) {
    tokenFilter.assertInternal(request);
    searchService.upsertLive(body);
  }

  @DeleteMapping("/lives/{sessionId}")
  public void deleteLive(HttpServletRequest request, @PathVariable("sessionId") String sessionId) {
    tokenFilter.assertInternal(request);
    searchService.deleteLive(sessionId);
  }
}
