package com.onepage.search.service;

import com.onepage.search.client.ContentClient;
import com.onepage.search.client.LiveClient;
import com.onepage.search.client.UserClient;
import com.onepage.search.dto.SearchDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动和定时把公开笔记、用户、启用话题、在播场次拉进 ES。MQ 未消费时靠这一条补齐历史。 */
@Component
public class IndexBackfill {
  private static final Logger log = LoggerFactory.getLogger(IndexBackfill.class);
  private static final int PAGE = 50;
  private static final int STARTUP_TRIES = 8;
  private static final long STARTUP_RETRY_MS = 2_000L;

  private final ContentClient contentClient;
  private final UserClient userClient;
  private final LiveClient liveClient;
  private final SearchService searchService;
  private final String internalToken;

  public IndexBackfill(
      ContentClient contentClient,
      UserClient userClient,
      LiveClient liveClient,
      SearchService searchService,
      @Value("${onepage.internal-token}") String internalToken) {
    this.contentClient = contentClient;
    this.userClient = userClient;
    this.liveClient = liveClient;
    this.searchService = searchService;
    this.internalToken = internalToken;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    for (int attempt = 1; attempt <= STARTUP_TRIES; attempt++) {
      if (run("startup")) {
        return;
      }
      log.warn("search backfill startup 第 {}/{} 次未齐（对端可能还没进 Nacos），{}ms 后再试",
          attempt, STARTUP_TRIES, STARTUP_RETRY_MS);
      sleepQuiet();
    }
  }

  @Scheduled(fixedDelay = 600_000L, initialDelay = 30_000L)
  public void scheduled() {
    run("schedule");
  }

  /** 四段各自吞错；全成功才 true。避免 user 未就绪时话题/直播也不回填。 */
  private boolean run(String reason) {
    boolean posts = safe("posts", this::fillPosts);
    boolean users = safe("users", this::fillUsers);
    boolean topics = safe("topics", this::fillTopics);
    boolean lives = safe("lives", this::fillLives);
    boolean ok = posts && users && topics && lives;
    if (ok) {
      log.info("search backfill {} done", reason);
    }
    return ok;
  }

  private boolean safe(String part, Runnable action) {
    try {
      action.run();
      return true;
    } catch (RuntimeException ex) {
      log.warn("search backfill {} failed: {}", part, ex.getMessage());
      return false;
    }
  }

  private void fillPosts() {
    String cursor = null;
    for (int i = 0; i < 200; i++) {
      SearchDtos.PostPage page =
          contentClient.searchablePosts(internalToken, cursor, PAGE);
      if (page == null || page.items() == null || page.items().isEmpty()) {
        return;
      }
      for (SearchDtos.PostBrief item : page.items()) {
        searchService.upsertPost(item);
      }
      if (!page.hasMore() || page.nextCursor() == null) {
        return;
      }
      cursor = page.nextCursor();
    }
  }

  private void fillUsers() {
    String cursor = null;
    for (int i = 0; i < 200; i++) {
      SearchDtos.UserPage page =
          userClient.searchableUsers(internalToken, cursor, PAGE);
      if (page == null || page.items() == null || page.items().isEmpty()) {
        return;
      }
      for (SearchDtos.UserDoc item : page.items()) {
        searchService.upsertUser(item);
      }
      if (!page.hasMore() || page.nextCursor() == null) {
        return;
      }
      cursor = page.nextCursor();
    }
  }

  private void fillTopics() {
    String cursor = null;
    for (int i = 0; i < 200; i++) {
      SearchDtos.TopicPage page =
          contentClient.searchableTopics(internalToken, cursor, PAGE);
      if (page == null || page.items() == null || page.items().isEmpty()) {
        return;
      }
      for (SearchDtos.TopicDoc item : page.items()) {
        searchService.upsertTopic(item);
      }
      if (!page.hasMore() || page.nextCursor() == null) {
        return;
      }
      cursor = page.nextCursor();
    }
  }

  private void fillLives() {
    SearchDtos.LivePage page = liveClient.searchableLives(internalToken);
    searchService.replaceLives(page == null ? List.of() : page.items());
  }

  private static void sleepQuiet() {
    try {
      Thread.sleep(STARTUP_RETRY_MS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
