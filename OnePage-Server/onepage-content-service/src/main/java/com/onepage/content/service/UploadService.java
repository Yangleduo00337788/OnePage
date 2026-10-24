package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.common.tx.AfterCommit;
import com.onepage.content.client.UserClient;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.MediaAsset;
import com.onepage.content.entity.PostMedia;
import com.onepage.content.mapper.MediaAssetMapper;
import com.onepage.content.mapper.PostMediaMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
      Set.of("avatar", "cover", "post_image", "post_video", "im_image", "feedback");
  private static final Set<String> IMAGE_MIMES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
  private static final Set<String> VIDEO_MIMES =
      Set.of("video/mp4", "video/quicktime", "video/webm");
  private final MediaAssetMapper mapper;
  private final PostMediaMapper postMediaMapper;
  private final MinioClient minioClient;
  private final AuthorLookup authorLookup;
  private final UserClient userClient;
  private final CommunityPolicyCache policyCache;
  private final String bucket;
  private final int expirySec;
  private final String mediaPublicBase;
  private final String internalToken;
  private final PostReviewService postReviewService;

  public UploadService(
      MediaAssetMapper mapper,
      PostMediaMapper postMediaMapper,
      MinioClient minioClient,
      AuthorLookup authorLookup,
      UserClient userClient,
      CommunityPolicyCache policyCache,
      @Lazy PostReviewService postReviewService,
      @Value("${onepage.minio.bucket}") String bucket,
      @Value("${onepage.minio.presign-expiry-sec}") int expirySec,
      @Value("${onepage.media-public-base}") String mediaPublicBase,
      @Value("${onepage.internal-token}") String internalToken) {
    this.mapper = mapper;
    this.postMediaMapper = postMediaMapper;
    this.minioClient = minioClient;
    this.authorLookup = authorLookup;
    this.userClient = userClient;
    this.policyCache = policyCache;
    this.bucket = bucket;
    this.expirySec = expirySec;
    this.mediaPublicBase = mediaPublicBase == null ? "" : mediaPublicBase.replaceAll("/+$", "");
    this.internalToken = internalToken;
    this.postReviewService = postReviewService;
  }

  /**
   * objectKey 带 purpose/userId/uuid，避免所有文件堆在桶根目录、也避免猜到别人路径。
   * extraHeaders Content-Type 必须和浏览器 PUT 时一致，否则签名校验失败。
   */
  public PresignResult presign(Long userId, String mimeType, long sizeBytes, String purpose) {
    if (!PURPOSES.contains(purpose)) {
      throw BusinessException.validation("purpose 不支持");
    }
    if (sizeBytes <= 0) {
      throw BusinessException.validation("文件大小不合法");
    }
    CommunityPolicyCache.Snapshot policy = policyCache.current();
    long imageMax = policy.imageMaxMb() * 1024L * 1024L;
    long videoMax = policy.videoMaxMb() * 1024L * 1024L;
    if ("avatar".equals(purpose) || "cover".equals(purpose) || "post_image".equals(purpose)
        || "im_image".equals(purpose) || "feedback".equals(purpose)) {
      if (!IMAGE_MIMES.contains(mimeType)) {
        throw BusinessException.validation("只支持 jpg / png / webp / gif");
      }
      if (sizeBytes > imageMax) {
        throw BusinessException.validation("图片不能超过 " + policy.imageMaxMb() + "MB");
      }
    } else if ("post_video".equals(purpose)) {
      if (!VIDEO_MIMES.contains(mimeType)) {
        throw BusinessException.validation("只支持 mp4 / mov / webm");
      }
      if (sizeBytes > videoMax) {
        throw BusinessException.validation("视频不能超过 " + policy.videoMaxMb() + "MB");
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
    AfterCommit.run(() -> postReviewService.enqueueMedia(mediaId));
    String url = mediaPublicBase.isEmpty() || asset.getObjectKey() == null
        ? ""
        : mediaPublicBase + "/" + asset.getObjectKey();
    return new CompleteResult(String.valueOf(asset.getId()), "ready", url);
  }

  public MediaAsset require(Long mediaId) {
    MediaAsset asset = mapper.selectById(mediaId);
    if (asset == null) {
      throw BusinessException.notFound("媒体不存在");
    }
    return asset;
  }

  public FeedDtos.OpsMediaPage opsPage(
      String q, String purpose, String status, String hidden, String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    if (StringUtils.hasText(purpose) && !PURPOSES.contains(purpose.trim())) {
      throw BusinessException.validation("purpose 不支持");
    }
    LambdaQueryWrapper<MediaAsset> query = Wrappers.lambdaQuery();
    if (StringUtils.hasText(purpose)) {
      query.likeRight(MediaAsset::getObjectKey, purpose.trim() + "/");
    }
    if ("pending".equals(status)) {
      query.eq(MediaAsset::getStatus, 0);
    } else if ("ready".equals(status)) {
      query.eq(MediaAsset::getStatus, 1);
    } else if ("fail".equals(status)) {
      query.eq(MediaAsset::getStatus, 2);
    }
    if ("yes".equals(hidden)) {
      query.isNotNull(MediaAsset::getBlockedAt);
    } else if ("no".equals(hidden)) {
      query.isNull(MediaAsset::getBlockedAt);
    }
    if (StringUtils.hasText(q)) {
      try {
        long id = Long.parseLong(q.trim());
        query.and(w -> w.eq(MediaAsset::getId, id).or().eq(MediaAsset::getOwnerId, id));
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("只支持媒体 ID 或上传人 ID");
      }
    }
    if (StringUtils.hasText(cursor)) {
      try {
        query.lt(MediaAsset::getId, Long.parseLong(cursor.trim()));
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("cursor 不合法");
      }
    }
    query.orderByDesc(MediaAsset::getId).last("LIMIT " + (size + 1));
    List<MediaAsset> rows = mapper.selectList(query);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    List<FeedDtos.OpsMediaRow> items = new ArrayList<>();
    for (MediaAsset asset : rows) {
      items.add(toOpsRow(asset));
    }
    String next = null;
    if (hasMore && !rows.isEmpty() && rows.getLast().getId() != null) {
      next = String.valueOf(rows.getLast().getId());
    }
    return new FeedDtos.OpsMediaPage(items, next, hasMore);
  }

  @Transactional
  public void opsHide(long mediaId) {
    MediaAsset asset = require(mediaId);
    if (asset.getBlockedAt() != null) {
      return;
    }
    mapper.update(
        null,
        Wrappers.<MediaAsset>lambdaUpdate()
            .eq(MediaAsset::getId, mediaId)
            .set(MediaAsset::getBlockedAt, LocalDateTime.now()));
    String purpose = purposeOf(asset.getObjectKey());
    if (("avatar".equals(purpose) || "cover".equals(purpose)) && StringUtils.hasText(asset.getObjectKey())) {
      try {
        userClient.clearObjectKey(internalToken, asset.getObjectKey());
      } catch (RuntimeException ex) {
        throw new IllegalStateException("清头像/封面失败", ex);
      }
    }
  }

  @Transactional
  public void opsRestore(long mediaId) {
    MediaAsset asset = require(mediaId);
    if (asset.getBlockedAt() == null) {
      return;
    }
    mapper.update(
        null,
        Wrappers.<MediaAsset>lambdaUpdate()
            .eq(MediaAsset::getId, mediaId)
            .set(MediaAsset::getBlockedAt, null));
  }

  private FeedDtos.OpsMediaRow toOpsRow(MediaAsset asset) {
    int code = asset.getStatus() == null ? 0 : asset.getStatus();
    String status = code == 1 ? "ready" : (code == 2 ? "fail" : "pending");
    String url = "";
    if (code == 1 && StringUtils.hasText(asset.getObjectKey()) && StringUtils.hasText(mediaPublicBase)) {
      url = mediaPublicBase + "/" + asset.getObjectKey();
    }
    String postId = "";
    if (asset.getId() != null) {
      List<PostMedia> links = postMediaMapper.selectList(
          Wrappers.<PostMedia>lambdaQuery()
              .eq(PostMedia::getMediaId, asset.getId())
              .last("LIMIT 1"));
      if (links != null && !links.isEmpty() && links.getFirst().getPostId() != null) {
        postId = String.valueOf(links.getFirst().getPostId());
      }
    }
    return new FeedDtos.OpsMediaRow(
        String.valueOf(asset.getId()),
        authorLookup.brief(asset.getOwnerId()),
        purposeOf(asset.getObjectKey()),
        asset.getMimeType() == null ? "" : asset.getMimeType(),
        asset.getSizeBytes() == null ? 0L : asset.getSizeBytes(),
        status,
        url,
        postId,
        asset.getBlockedAt() != null,
        asset.getCreatedAt() == null ? "" : asset.getCreatedAt().toString());
  }

  private static String purposeOf(String objectKey) {
    if (!StringUtils.hasText(objectKey)) {
      return "";
    }
    int slash = objectKey.indexOf('/');
    return slash <= 0 ? objectKey : objectKey.substring(0, slash);
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
  public record CompleteResult(String mediaId, String status, String url) {}
}
