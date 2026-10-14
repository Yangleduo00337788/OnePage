package com.onepage.user.dto;

import java.util.List;

public record ReceivedCursorPage(List<ReceivedItemView> items, String nextCursor, boolean hasMore) {}
