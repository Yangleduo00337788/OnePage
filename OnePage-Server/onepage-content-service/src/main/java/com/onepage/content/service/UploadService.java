package com.onepage.content.service;

import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.entity.MediaAsset;
import com.onepage.content.mapper.MediaAssetMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 预签名：先在库里插一条 status=0 的媒体，再签 PUT URL。
 * complete：用 StatObject 确认对象真的在桶里，再把 status 改为 1。
 * 不这样的话前端可以拿一个没上传成功的 mediaId 去改头像。
 */
@Service
public class UploadService {
  private static final Set<String> PURPOSES =
      Set.of("avatar", "cover", "post_image", "post_video", "im_image");
  private static final Set<String> IM_IMAGE_MIMES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
  private static final long IM_IMAGE_MAX_BYTES = 10L * 1024 * 1024;
  private final MediaAssetMapper mapper;
  private final MinioClient minioClient;
  private final String bucket;
  private final int expirySec;

  public UploadService(
      MediaAssetMapper mapper,
      MinioClient minioClient,
      @Value("${onepage.minio.bucket}") String bucket,
      @Value("${onepage.minio.presign-expiry-sec}") int expirySec) {
    this.mapper = mapper;
    this.minioClient = minioClient;
    this.bucket = bucket;
    this.expirySec = expirySec;
  }

  /**
   * objectKey 带 purpose/userId/uuid，避免所有文件堆在桶根目录、也避免猜到别人路径。
   * extraHeaders Content-Type 必须和浏览器 PUT 时一致，否则签名校验失败。
   */
  public PresignResult presign(Long userId, String mimeType, long sizeBytes, String purpose) {
    if (!PURPOSES.contains(purpose)) {
      throw BusinessException.validation("purpose 不支持");
    }
    if (sizeBytes <= 0 || sizeBytes > 200L * 1024 * 1024) {
      throw BusinessException.validation("文件大小不合法");
    }
    if ("im_image".equals(purpose)) {
      if (!IM_IMAGE_MIMES.contains(mimeType)) {
        throw BusinessException.validation("只支持 jpg / png / webp / gif");
      }
      if (sizeBytes > IM_IMAGE_MAX_BYTES) {
        throw BusinessException.validation("图片不能超过 10MB");
      }
    }
    long id = IdGenerator.nextId();
    String ext = extension(mimeType);
    String objectKey = purpose + "/" + userId + "/" + UUID.randomUUID() + ext;
    MediaAsset asset = new MediaAsset();
    asset.setId(id);
    asset.setOwnerId(userId);
    asset.setBucket(bucket);
    asset.setObjectKey(objectKey);
    asset.setMimeType(mimeType);
    asset.setSizeBytes(sizeBytes);
    asset.setStatus(0);
    asset.setCreatedAt(LocalDateTime.now());
    mapper.insert(asset);
    try {
      String url = minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.PUT)
              .bucket(bucket)
              .object(objectKey)
              .expiry(expirySec, TimeUnit.SECONDS)
              .extraHeaders(Map.of("Content-Type", mimeType))
              .build());
      return new PresignResult(String.valueOf(id), url, objectKey, LocalDateTime.now().plusSeconds(expirySec).toString());
    } catch (Exception ex) {
      throw new IllegalStateException("签发上传地址失败", ex);
    }
  }

  public CompleteResult complete(Long userId, Long mediaId) {
    MediaAsset asset = mapper.selectById(mediaId);
    if (asset == null || !userId.equals(asset.getOwnerId())) {
      throw BusinessException.notFound("媒体不存在");
    }
    try {
      minioClient.statObject(StatObjectArgs.builder().bucket(asset.getBucket()).object(asset.getObjectKey()).build());
    } catch (Exception ex) {
      throw BusinessException.validation("文件尚未上传完成");
    }
    asset.setStatus(1);
    mapper.updateById(asset);
    return new CompleteResult(String.valueOf(asset.getId()), "ready");
  }

  public MediaAsset require(Long mediaId) {
    MediaAsset asset = mapper.selectById(mediaId);
    if (asset == null) {
      throw BusinessException.notFound("媒体不存在");
    }
    return asset;
  }

  private String extension(String mime) {
    if (mime == null) {
      return "";
    }
    return switch (mime) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/gif" -> ".gif";
      case "video/mp4" -> ".mp4";
      case "video/quicktime" -> ".mov";
      case "video/webm" -> ".webm";
      default -> "";
    };
  }

  public record PresignResult(String mediaId, String uploadUrl, String objectKey, String expiresAt) {}
  public record CompleteResult(String mediaId, String status) {}
}
