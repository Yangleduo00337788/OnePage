package com.onepage.live.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** 开播绑封面时核媒体归属与就绪态。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @GetMapping("/internal/media/{mediaId}")
  MediaInfo getMedia(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("mediaId") Long mediaId);

  record MediaInfo(Long mediaId, Long ownerId, String objectKey, Integer status) {}
}
