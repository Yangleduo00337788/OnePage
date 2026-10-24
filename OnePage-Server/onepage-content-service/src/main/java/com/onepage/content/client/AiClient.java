package com.onepage.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/** 公开笔记文字+图片预审。失败由调用方当成 skip。 */
@FeignClient(name = "onepage-ai-service", configuration = AiFeignConfig.class)
public interface AiClient {
  @PostMapping("/internal/ai/review")
  ReviewResult review(@RequestHeader("X-Internal-Token") String token, @RequestBody ReviewSpec body);

  record ReviewSpec(String postId, String title, String body, List<String> imageUrls) {}

  record ReviewResult(String verdict, String reason, Integer score, List<String> labels) {}
}
