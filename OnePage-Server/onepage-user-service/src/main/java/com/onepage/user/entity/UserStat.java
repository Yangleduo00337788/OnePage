package com.onepage.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 关注/粉丝/获赞计数单独一张表，避免每次改昵称都锁计数行。
 * P0 只初始化为 0，互动接口在后续阶段写。
 */
@TableName("user_stat")
public class UserStat {
  @TableId(type = IdType.INPUT)
  private Long userId;
  private Integer followingCount;
  private Integer followerCount;
  private Integer likeReceivedCount;
  private Integer favoriteReceivedCount;
  private LocalDateTime updatedAt;

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Integer getFollowingCount() { return followingCount; }
  public void setFollowingCount(Integer followingCount) { this.followingCount = followingCount; }
  public Integer getFollowerCount() { return followerCount; }
  public void setFollowerCount(Integer followerCount) { this.followerCount = followerCount; }
  public Integer getLikeReceivedCount() { return likeReceivedCount; }
  public void setLikeReceivedCount(Integer likeReceivedCount) { this.likeReceivedCount = likeReceivedCount; }
  public Integer getFavoriteReceivedCount() { return favoriteReceivedCount; }
  public void setFavoriteReceivedCount(Integer favoriteReceivedCount) { this.favoriteReceivedCount = favoriteReceivedCount; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
