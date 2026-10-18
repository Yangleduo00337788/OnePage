package com.onepage.content.client;

import com.onepage.content.dto.FeedDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 公开笔记写入搜索索引。搜索服务未启动时由调用方吞异常。 */
@FeignClient(name = "onepage-search-service")
public interface SearchClient {
  @PostMapping("/internal/index/posts")
  void upsertPost(@RequestHeader("X-Internal-Token") String token, @RequestBody FeedDtos.PostBrief body);

  @DeleteMapping("/internal/index/posts/{postId}")
  void deletePost(@RequestHeader("X-Internal-Token") String token, @PathVariable("postId") String postId);

  @PostMapping("/internal/index/topics")
  void upsertTopic(@RequestHeader("X-Internal-Token") String token, @RequestBody FeedDtos.TopicIndexDoc body);

  @DeleteMapping("/internal/index/topics/{topicId}")
  void deleteTopic(@RequestHeader("X-Internal-Token") String token, @PathVariable("topicId") String topicId);
}
