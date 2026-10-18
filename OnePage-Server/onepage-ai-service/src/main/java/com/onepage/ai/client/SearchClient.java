package com.onepage.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 耶耶 RAG：先查公开笔记与话题，再送给模型。字段对齐 search 的 {@code SearchResult}。 */
@FeignClient(name = "onepage-search-service")
public interface SearchClient {
  @GetMapping("/api/v1/search")
  SearchHit search(
      @RequestParam("q") String q,
      @RequestParam("type") String type,
      @RequestParam("limit") Integer limit);

  record SearchHit(List<PostHit> posts, List<TopicHit> topics) {}

  record PostHit(String postId, String title, String body, String coverUrl, Author author) {}

  record TopicHit(String topicId, String name) {}

  record Author(String nickname, String pageId) {}
}
