package com.onepage.content.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.MediaAsset;
import com.onepage.content.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给 user / message 查媒体归属。返回 objectKey、mime 和 status，不返回预签名 URL。
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

  @GetMapping("/media/ops")
  public FeedDtos.OpsMediaPage opsPage(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "purpose", required = false) String purpose,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return uploadService.opsPage(q, purpose, status, hidden, cursor, limit);
  }

  @PostMapping("/media/{mediaId}/hide")
  public ResponseEntity<Void> hide(HttpServletRequest request, @PathVariable("mediaId") long mediaId) {
    assertInternal(request);
    uploadService.opsHide(mediaId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/media/{mediaId}/restore")
  public ResponseEntity<Void> restore(HttpServletRequest request, @PathVariable("mediaId") long mediaId) {
    assertInternal(request);
    uploadService.opsRestore(mediaId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/media/{mediaId}")
  public MediaInfo get(HttpServletRequest request, @PathVariable("mediaId") Long mediaId) {
    assertInternal(request);
    MediaAsset asset = uploadService.require(mediaId);
    int status = asset.getStatus() == null ? 0 : asset.getStatus();
    if (asset.getBlockedAt() != null) {
      status = 2;
    }
    return new MediaInfo(
        asset.getId(),
        asset.getOwnerId(),
        asset.getObjectKey(),
        asset.getMimeType(),
        status);
  }

  public record MediaInfo(
      Long mediaId, Long ownerId, String objectKey, String mimeType, Integer status) {}

  private void assertInternal(HttpServletRequest request) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
  }
}
