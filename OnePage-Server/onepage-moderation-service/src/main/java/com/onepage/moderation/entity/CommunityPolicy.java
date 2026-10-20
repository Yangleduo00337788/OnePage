package com.onepage.moderation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 单行社区规则。id 固定为 1。 */
@TableName("community_policy")
public class CommunityPolicy {
  @TableId(type = IdType.INPUT)
  private Integer id;
  private String bannedWords;
  private Integer nicknameMax;
  private Integer bioMax;
  private Integer imageMaxMb;
  private Integer videoMaxMb;
  private LocalDateTime updatedAt;

  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }
  public String getBannedWords() { return bannedWords; }
  public void setBannedWords(String bannedWords) { this.bannedWords = bannedWords; }
  public Integer getNicknameMax() { return nicknameMax; }
  public void setNicknameMax(Integer nicknameMax) { this.nicknameMax = nicknameMax; }
  public Integer getBioMax() { return bioMax; }
  public void setBioMax(Integer bioMax) { this.bioMax = bioMax; }
  public Integer getImageMaxMb() { return imageMaxMb; }
  public void setImageMaxMb(Integer imageMaxMb) { this.imageMaxMb = imageMaxMb; }
  public Integer getVideoMaxMb() { return videoMaxMb; }
  public void setVideoMaxMb(Integer videoMaxMb) { this.videoMaxMb = videoMaxMb; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
