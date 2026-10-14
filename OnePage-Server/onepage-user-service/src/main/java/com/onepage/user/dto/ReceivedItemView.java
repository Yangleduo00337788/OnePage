package com.onepage.user.dto;

/** 获赞/被收藏一行：谁互动了哪篇笔记。 */
public record ReceivedItemView(
    UserBriefView actor, String postId, String postTitle, String createdAt) {}
