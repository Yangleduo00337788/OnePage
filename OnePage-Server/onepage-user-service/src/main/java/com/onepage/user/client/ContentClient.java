package com.onepage.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** 通过 Nacos 发现 content-service，改头像前核对媒体归属。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @GetMapping("/internal/media/{mediaId}")
  MediaInfo getMedia(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("mediaId") Long mediaId);

  record MediaInfo(Long mediaId, Long ownerId, String objectKey, Integer status) {}
}
