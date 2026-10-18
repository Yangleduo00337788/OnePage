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
    run("startup");
  }

  @Scheduled(fixedDelay = 600_000L, initialDelay = 30_000L)
  public void scheduled() {
    run("schedule");
  }

  private void run(String reason) {
    try {
      fillPosts();
      fillUsers();
      fillTopics();
      fillLives();
      log.info("search backfill {} done", reason);
    } catch (RuntimeException ex) {
      log.warn("search backfill {} failed", reason, ex);
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
    try {
      SearchDtos.LivePage page = liveClient.searchableLives(internalToken);
      searchService.replaceLives(page == null ? List.of() : page.items());
    } catch (RuntimeException ex) {
      log.warn("search backfill lives failed", ex);
    }
  }
}
