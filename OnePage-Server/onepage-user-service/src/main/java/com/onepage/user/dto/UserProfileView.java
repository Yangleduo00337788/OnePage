package com.onepage.user.dto;

/**
 * 返回给前端的完整资料。avatarUrl/coverUrl 已是可打开的 HTTP 地址，不是 objectKey。
 * 嵌套 {@code Stats}：JSON 里是 {@code stats: { followingCount, ... }}。
 */
public record UserProfileView(
    String userId,
    String pageId,
    String nickname,
    String avatarUrl,
    String coverUrl,
    String bio,
    int gender,
    boolean followedByMe,
    Stats stats
) {
  public record Stats(
      int followingCount,
      int followerCount,
      int likeReceivedCount,
      int favoriteReceivedCount
  ) {}
}
