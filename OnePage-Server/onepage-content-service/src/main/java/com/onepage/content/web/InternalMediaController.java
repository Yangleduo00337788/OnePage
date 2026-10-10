package com.onepage.content.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import com.onepage.content.entity.MediaAsset;
import com.onepage.content.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给 user-service 查媒体归属。返回 objectKey 和 status，不返回预签名 URL。
 */
@RestController
@RequestMapping("/internal")
public class InternalMediaController {
  private final UploadService uploadService;
  private final String expectedToken;

  public InternalMediaController(
      UploadService uploadService,
      @Value("${onepage.internal-token}") String expectedToken) {
    this.uploadService = uploadService;
    this.expectedToken = expectedToken;
  }

  @GetMapping("/media/{mediaId}")
  public MediaInfo get(HttpServletRequest request, @PathVariable("mediaId") Long mediaId) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
    MediaAsset asset = uploadService.require(mediaId);
    return new MediaInfo(asset.getId(), asset.getOwnerId(), asset.getObjectKey(), asset.getStatus());
  }

  public record MediaInfo(Long mediaId, Long ownerId, String objectKey, Integer status) {}
}
