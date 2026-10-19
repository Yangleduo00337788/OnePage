package com.onepage.user.dto;

/** 关注列表卡片。followedByMe：当前登录者是否已关注这一行。 */
public record UserBriefView(
    String userId,
    String pageId,
    String nickname,
    String avatarUrl,
    boolean followedByMe,
    boolean liveHost,
    boolean verified,
    boolean official) {}
