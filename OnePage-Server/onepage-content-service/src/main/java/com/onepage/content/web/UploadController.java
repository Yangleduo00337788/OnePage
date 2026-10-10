package com.onepage.content.web;

import com.onepage.common.web.CurrentUser;
import com.onepage.content.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 浏览器上传两步走：先 presign 拿临时 PUT 地址，直传 MinIO，再 complete 标记就绪。
 * 文件不经过 Java，避免大图撑爆网关。必须已登录（CurrentUser）。
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
  private final UploadService uploadService;

  public UploadController(UploadService uploadService) {
    this.uploadService = uploadService;
  }

  @PostMapping("/presign")
  public UploadService.PresignResult presign(HttpServletRequest request, @Valid @RequestBody PresignRequest body) {
    return uploadService.presign(CurrentUser.requireUserId(request), body.mimeType(), body.sizeBytes(), body.purpose());
  }

  @PostMapping("/complete")
  public UploadService.CompleteResult complete(HttpServletRequest request, @Valid @RequestBody CompleteRequest body) {
    return uploadService.complete(CurrentUser.requireUserId(request), Long.parseLong(body.mediaId()));
  }

  public record PresignRequest(@NotBlank String mimeType, @Positive long sizeBytes, @NotBlank String purpose) {}
  public record CompleteRequest(@NotBlank String mediaId) {}
}
