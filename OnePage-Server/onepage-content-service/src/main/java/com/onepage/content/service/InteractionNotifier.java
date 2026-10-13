package com.onepage.content.service;

import com.onepage.common.tx.AfterCommit;
import com.onepage.content.client.NotificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 赞评投递。失败只打日志，不回滚业务。 */
@Component
public class InteractionNotifier {
  private static final Logger log = LoggerFactory.getLogger(InteractionNotifier.class);

  private final NotificationClient notificationClient;
  private final String internalToken;

  public InteractionNotifier(
      NotificationClient notificationClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.notificationClient = notificationClient;
    this.internalToken = internalToken;
  }

  public void emit(Long recipientId, String type, long actorId, String aggregateKey, Long postId, Long commentId) {
    emit(recipientId, type, actorId, aggregateKey, postId, commentId, null);
  }

  public void emit(
      Long recipientId,
      String type,
      long actorId,
      String aggregateKey,
      Long postId,
      Long commentId,
      String preview) {
    if (recipientId == null || recipientId == actorId) {
      return;
    }
    AfterCommit.run(() -> {
      try {
        notificationClient.ingest(
            internalToken,
            new NotificationClient.IngestRequest(
                recipientId, type, actorId, aggregateKey, postId, commentId, preview));
      } catch (RuntimeException ex) {
        log.warn("notify {} to {} failed", type, recipientId, ex);
      }
    });
  }
}
