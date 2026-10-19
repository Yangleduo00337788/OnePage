package com.onepage.common.dto;

/**
 * 跨服务的用户摘要（登录成功、Feign 建档回包）。
 * 放 common 是因为 auth 和 user 都要序列化同一份 JSON，避免两边各写一套字段对不上。
 * id 用 String：JS 安全整数只有 53 位，Long 雪花 id 直接当 number 会丢精度。
 */
public record UserSummary(
    String userId,
    String pageId,
    String nickname,
    String avatarUrl,
    boolean liveHost,
    boolean verified
) {}
