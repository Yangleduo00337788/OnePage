package com.onepage.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 媒体元数据。真正的字节在 MinIO，这里只记 key 和状态。
 * status：0 待上传，1 已确认。durationSec 给视频预留，P0 图片可空。
 */
@TableName("media_asset")
public class MediaAsset {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long ownerId;
  private String bucket;
  private String objectKey;
  private String mimeType;
  private Long sizeBytes;
  private Integer durationSec;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime blockedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getOwnerId() { return ownerId; }
  public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
  public String getBucket() { return bucket; }
  public void setBucket(String bucket) { this.bucket = bucket; }
  public String getObjectKey() { return objectKey; }
  public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
  public String getMimeType() { return mimeType; }
  public void setMimeType(String mimeType) { this.mimeType = mimeType; }
  public Long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
  public Integer getDurationSec() { return durationSec; }
  public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
  public Integer getStatus() { return status; }
  public void setStatus(Integer status) { this.status = status; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getBlockedAt() { return blockedAt; }
  public void setBlockedAt(LocalDateTime blockedAt) { this.blockedAt = blockedAt; }
}
