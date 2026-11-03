package com.onepage.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.content.dto.FeedDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 公开发现流首页缓存在 Redis，多实例共享。Redis 失败则回源查库，不挡首页。
 */
@Component
public class ExplorePageCache {
  private static final Duration TTL = Duration.ofSeconds(1);
  private static final String KEY_PREFIX = "onepage:feed:explore:v1:";
  private static final Logger log = LoggerFactory.getLogger(ExplorePageCache.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<String, CompletableFuture<FeedDtos.CursorPage>> inflight =
      new ConcurrentHashMap<>();

  public ExplorePageCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  /**
   * 同一缓存键只查库一次，其余请求等这一次的结果，避免千并发同时打穿 Redis 和连接池。
   */
  public FeedDtos.CursorPage loadShared(
      String category, Integer limit, Supplier<FeedDtos.CursorPage> loader) {
    FeedDtos.CursorPage cached = get(category, limit);
    if (cached != null) {
      return cached;
    }
    String cacheKey = key(category, limit);
    CompletableFuture<FeedDtos.CursorPage> mine = new CompletableFuture<>();
    CompletableFuture<FeedDtos.CursorPage> winner = inflight.putIfAbsent(cacheKey, mine);
    if (winner == null) {
      try {
        FeedDtos.CursorPage page = loader.get();
        put(category, limit, page);
        mine.complete(page);
        return page;
      } catch (RuntimeException ex) {
        mine.completeExceptionally(ex);
        throw ex;
      } finally {
        inflight.remove(cacheKey, mine);
      }
    }
    try {
      return winner.join();
    } catch (CompletionException ex) {
      return loader.get();
    }
  }

  public FeedDtos.CursorPage get(String category, Integer limit) {
    try {
      String raw = redis.opsForValue().get(key(category, limit));
      if (raw == null || raw.isBlank()) {
        return null;
      }
      return objectMapper.readValue(raw, FeedDtos.CursorPage.class);
    } catch (RuntimeException | JsonProcessingException ex) {
      log.warn("发现流缓存读取失败，回源查库: {}", ex.getClass().getSimpleName());
      return null;
    }
  }

  public void put(String category, Integer limit, FeedDtos.CursorPage page) {
    if (page == null) {
      return;
    }
    try {
      redis.opsForValue().set(key(category, limit), objectMapper.writeValueAsString(page), TTL);
    } catch (RuntimeException | JsonProcessingException ex) {
      log.warn("发现流缓存写入失败: {}", ex.getClass().getSimpleName());
    }
  }

  public void evict() {
    try {
      ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(64).build();
      List<String> batch = new ArrayList<>();
      try (Cursor<String> cursor = redis.scan(options)) {
        while (cursor.hasNext()) {
          batch.add(cursor.next());
          if (batch.size() >= 64) {
            redis.delete(batch);
            batch.clear();
          }
        }
      }
      if (!batch.isEmpty()) {
        redis.delete(batch);
      }
    } catch (RuntimeException ex) {
      log.warn("发现流缓存失效失败: {}", ex.getClass().getSimpleName());
    }
  }

  private static String key(String category, Integer limit) {
    String cat = category == null ? "" : category.trim();
    int size = limit == null ? 20 : limit;
    return KEY_PREFIX + cat + ":" + size;
  }
}
