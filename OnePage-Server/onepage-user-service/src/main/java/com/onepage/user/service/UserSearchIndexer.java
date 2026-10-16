package com.onepage.user.service;

import com.onepage.common.tx.AfterCommit;
import com.onepage.user.client.SearchClient;
import com.onepage.user.entity.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 事务提交后同步用户到 ES。失败只打日志。 */
@Component
public class UserSearchIndexer {
  private static final Logger log = LoggerFactory.getLogger(UserSearchIndexer.class);

  private final SearchClient searchClient;
  private final String internalToken;

  public UserSearchIndexer(
      SearchClient searchClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.searchClient = searchClient;
    this.internalToken = internalToken;
  }

  public void sync(UserProfile profile, String avatarUrl) {
    if (profile == null || profile.getUserId() == null) {
      return;
    }
    AfterCommit.run(() -> {
      try {
        searchClient.upsertUser(
            internalToken,
            new SearchClient.UserIndexDoc(
                String.valueOf(profile.getUserId()),
                profile.getPageId(),
                profile.getNickname(),
                avatarUrl == null ? "" : avatarUrl,
                profile.getBio() == null ? "" : profile.getBio()));
      } catch (RuntimeException ex) {
        log.warn("search index user {} failed", profile.getUserId(), ex);
      }
    });
  }
}
