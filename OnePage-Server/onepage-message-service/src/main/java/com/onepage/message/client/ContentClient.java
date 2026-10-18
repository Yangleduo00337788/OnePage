package com.onepage.message.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** 发图片时核对 media 归属与是否已 complete。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @GetMapping("/internal/media/{mediaId}")
  MediaInfo getMedia(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("mediaId") Long mediaId);

  record MediaInfo(Long mediaId, Long ownerId, String objectKey, String mimeType, Integer status) {}
}
