package com.onepage.user.dto;

import java.util.List;

public record UserCursorPage(List<UserBriefView> items, String nextCursor, boolean hasMore) {}
