package com.onepage.content.service;

import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.client.UserClient;
import com.onepage.content.dto.FeedDtos;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Feign 拉作者；失败时用 pageId 占位，Feed 不因 user 抖动整页 500。 */
@Component
public class AuthorLookup {
  private static final Logger log = LoggerFactory.getLogger(AuthorLookup.class);
  private final UserClient userClient;
  private final String internalToken;

  public AuthorLookup(UserClient userClient, @Value("${onepage.internal-token}") String internalToken) {
    this.userClient = userClient;
    this.internalToken = internalToken;
  }

  public FeedDtos.UserBrief brief(Long userId) {
    if (userId == null) {
      return empty();
    }
    return briefs(List.of(userId)).getOrDefault(userId, fallback(userId));
  }

  /** 一页一次。空 id 不打用户服务。 */
  public Map<Long, FeedDtos.UserBrief> briefs(Collection<Long> userIds) {
    Map<Long, FeedDtos.UserBrief> out = new HashMap<>();
    if (userIds == null || userIds.isEmpty()) {
      return out;
    }
    LinkedHashSet<Long> unique = new LinkedHashSet<>();
    for (Long userId : userIds) {
      if (userId != null) {
        unique.add(userId);
      }
    }
    if (unique.isEmpty()) {
      return out;
    }
    try {
      List<UserSummary> rows = userClient.briefs(internalToken, new ArrayList<>(unique));
      if (rows != null) {
        for (UserSummary summary : rows) {
          if (summary == null || summary.userId() == null) {
            continue;
          }
          try {
            long id = Long.parseLong(summary.userId());
            out.put(id, toBrief(summary, id));
          } catch (NumberFormatException ignored) {
            // 摘要 id 不是数字就跳过，后面用占位
          }
        }
      }
    } catch (FeignException.ServiceUnavailable ex) {
      log.warn("拉作者摘要失败（onepage-user-service 未在 Nacos）size={}", unique.size());
    } catch (Exception ex) {
      log.warn("拉作者摘要失败 size={}：{}", unique.size(), ex.getMessage());
    }
    for (Long userId : unique) {
      out.putIfAbsent(userId, fallback(userId));
    }
    return out;
  }

  private static FeedDtos.UserBrief toBrief(UserSummary summary, long userId) {
    return new FeedDtos.UserBrief(
        summary.userId(),
        summary.pageId() == null ? IdGenerator.pageId(userId) : summary.pageId(),
        summary.nickname() == null ? "用户" : summary.nickname(),
        summary.avatarUrl() == null ? "" : summary.avatarUrl(),
        summary.liveHost(),
        summary.verified(),
        summary.official());
  }

  private static FeedDtos.UserBrief empty() {
    return new FeedDtos.UserBrief("", "", "用户", "", false, false, false);
  }

  private static FeedDtos.UserBrief fallback(Long userId) {
    return new FeedDtos.UserBrief(String.valueOf(userId), IdGenerator.pageId(userId), "用户", "", false, false, false);
  }
}
