package com.onepage.user.service;

import com.onepage.common.text.BannedText;
import com.onepage.user.client.ModerationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** 15 秒缓存。治理挂了用上次或默认。 */
@Component
public class CommunityPolicyCache {
  private static final Logger log = LoggerFactory.getLogger(CommunityPolicyCache.class);
  static final Snapshot DEFAULT = new Snapshot(BannedText.DEFAULT_WORDS, 24, 160, 10, 200);
  private static final long TTL_MS = 15_000L;

  private final ModerationClient moderationClient;
  private final String internalToken;
  private volatile Snapshot cached;
  private volatile long expiresAt;

  public CommunityPolicyCache(
      ModerationClient moderationClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.moderationClient = moderationClient;
    this.internalToken = internalToken;
  }

  public Snapshot current() {
    long now = System.currentTimeMillis();
    if (cached != null && now < expiresAt) {
      return cached;
    }
    try {
      ModerationClient.PolicyView raw = moderationClient.policy(internalToken);
      if (raw != null) {
        Snapshot next = new Snapshot(
            raw.bannedWords() == null ? List.of() : List.copyOf(raw.bannedWords()),
            raw.nicknameMax() > 0 ? raw.nicknameMax() : DEFAULT.nicknameMax(),
            raw.bioMax() > 0 ? raw.bioMax() : DEFAULT.bioMax(),
            raw.imageMaxMb() > 0 ? raw.imageMaxMb() : DEFAULT.imageMaxMb(),
            raw.videoMaxMb() > 0 ? raw.videoMaxMb() : DEFAULT.videoMaxMb());
        cached = next;
        expiresAt = now + TTL_MS;
        return next;
      }
    } catch (RuntimeException ex) {
      log.warn("load community policy failed", ex);
    }
    return cached != null ? cached : DEFAULT;
  }

  public void assertClean(String text) {
    BannedText.assertClean(text, current().bannedWords());
  }

  public record Snapshot(
      List<String> bannedWords, int nicknameMax, int bioMax, int imageMaxMb, int videoMaxMb) {}
}
