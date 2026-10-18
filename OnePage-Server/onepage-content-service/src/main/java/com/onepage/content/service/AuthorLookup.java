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
      return new FeedDtos.UserBrief("", "", "用户", "");
    }
    try {
      UserSummary summary = userClient.summary(internalToken, userId);
      if (summary == null) {
        return fallback(userId);
      }
      return new FeedDtos.UserBrief(
          summary.userId(),
          summary.pageId(),
          summary.nickname() == null ? "用户" : summary.nickname(),
          summary.avatarUrl() == null ? "" : summary.avatarUrl());
    } catch (FeignException.ServiceUnavailable ex) {
      log.warn("拉作者摘要失败 userId={}（onepage-user-service 未在 Nacos）", userId);
      return fallback(userId);
    } catch (Exception ex) {
      log.warn("拉作者摘要失败 userId={}：{}", userId, ex.getMessage());
      return fallback(userId);
    }
  }

  private FeedDtos.UserBrief fallback(Long userId) {
    return new FeedDtos.UserBrief(String.valueOf(userId), IdGenerator.pageId(userId), "用户", "");
  }
}
