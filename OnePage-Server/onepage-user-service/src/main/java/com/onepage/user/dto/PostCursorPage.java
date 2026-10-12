package com.onepage.user.dto;

import java.util.List;
import java.util.Map;

/** 透传 content 的 `CursorPage<PostBrief>` JSON，字段名与 Feed 一致。 */
public record PostCursorPage(List<Map<String, Object>> items, String nextCursor, boolean hasMore) {}
