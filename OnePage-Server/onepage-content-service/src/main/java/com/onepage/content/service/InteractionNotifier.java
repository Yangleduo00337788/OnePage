package com.onepage.content.service;

import org.springframework.stereotype.Component;

/** 赞评投递。与评论同一事务写入 Outbox，提交后再 Feign。失败不回滚业务。 */
@Component
public class InteractionNotifier {
  private final NotifyOutboxRecorder notifyOutboxRecorder;

  public InteractionNotifier(NotifyOutboxRecorder notifyOutboxRecorder) {
    this.notifyOutboxRecorder = notifyOutboxRecorder;
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
    notifyOutboxRecorder.enqueue(recipientId, type, actorId, aggregateKey, postId, commentId, preview);
  }
}
