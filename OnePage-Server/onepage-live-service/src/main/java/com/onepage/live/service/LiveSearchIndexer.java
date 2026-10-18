package com.onepage.live.service;

import com.onepage.common.tx.AfterCommit;
import com.onepage.live.client.SearchClient;
import com.onepage.live.dto.LiveDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** 开播/关播后同步 ES。失败只打日志。 */
@Component
public class LiveSearchIndexer {
  private static final Logger log = LoggerFactory.getLogger(LiveSearchIndexer.class);
  private final LiveService liveService;
  private final SearchClient searchClient;
  private final String internalToken;

  public LiveSearchIndexer(
      @Lazy LiveService liveService,
      SearchClient searchClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.liveService = liveService;
    this.searchClient = searchClient;
    this.internalToken = internalToken;
  }

  public void sync(long sessionId) {
    AfterCommit.run(() -> {
      try {
        LiveDtos.LiveItem item = liveService.get(sessionId, true);
        if (item != null && "live".equals(item.status())) {
          searchClient.upsertLive(internalToken, item);
        } else {
          searchClient.deleteLive(internalToken, String.valueOf(sessionId));
        }
      } catch (RuntimeException ex) {
        log.warn("search index live {} failed", sessionId, ex);
      }
    });
  }

  public void remove(long sessionId) {
    AfterCommit.run(() -> {
      try {
        searchClient.deleteLive(internalToken, String.valueOf(sessionId));
      } catch (RuntimeException ex) {
        log.warn("search delete live {} failed", sessionId, ex);
      }
    });
  }
}
