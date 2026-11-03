package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.client.NotificationClient;
import com.onepage.content.entity.OutboxEvent;
import com.onepage.content.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 赞评通知先写入 outbox_event，再 Feign 投递。评论 HTTP 不等下游。
 */
@Service
public class NotifyOutboxRecorder {
  static final String AGGREGATE_TYPE = "notify";
  static final String EVENT_TYPE = "notify.ingest";
  private static final int STATUS_PENDING = 0;
  private static final int STATUS_SENT = 1;
  private static final int STATUS_FAILED = 2;
  private static final int DRAIN_LIMIT = 40;
  private static final Logger log = LoggerFactory.getLogger(NotifyOutboxRecorder.class);

  private final OutboxEventMapper outboxEventMapper;
  private final NotificationClient notificationClient;
  private final ObjectMapper objectMapper;
  private final Executor notifyExecutor;
  private final String internalToken;

  public NotifyOutboxRecorder(
      OutboxEventMapper outboxEventMapper,
      NotificationClient notificationClient,
      ObjectMapper objectMapper,
      @Qualifier("notifyExecutor") Executor notifyExecutor,
      @Value("${onepage.internal-token}") String internalToken) {
    this.outboxEventMapper = outboxEventMapper;
    this.notificationClient = notificationClient;
    this.objectMapper = objectMapper;
    this.notifyExecutor = notifyExecutor;
    this.internalToken = internalToken;
  }

  public void enqueue(
      Long recipientId,
      String type,
      long actorId,
      String aggregateKey,
      Long postId,
      Long commentId,
      String preview) {
    OutboxEvent event = new OutboxEvent();
    long eventId = IdGenerator.nextId();
    event.setId(eventId);
    event.setAggregateType(AGGREGATE_TYPE);
    event.setAggregateId(aggregateKey);
    event.setEventType(EVENT_TYPE);
    event.setStatus(STATUS_PENDING);
    event.setCreatedAt(LocalDateTime.now());
    try {
      event.setPayloadJson(objectMapper.writeValueAsString(
          new NotifyPayload(recipientId, type, actorId, aggregateKey, postId, commentId, preview)));
    } catch (JsonProcessingException ex) {
      log.error("notify outbox 序列化失败 type={} to={}", type, recipientId, ex);
      return;
    }
    outboxEventMapper.insert(event);
    try {
      notifyExecutor.execute(() -> drainOne(eventId));
    } catch (RejectedExecutionException ignored) {
      // 队列满时由定时任务补投，outbox 已落盘
    }
  }

  public void drainPending() {
    List<OutboxEvent> pending = outboxEventMapper.selectList(
        Wrappers.<OutboxEvent>lambdaQuery()
            .eq(OutboxEvent::getEventType, EVENT_TYPE)
            .eq(OutboxEvent::getStatus, STATUS_PENDING)
            .orderByAsc(OutboxEvent::getCreatedAt)
            .last("LIMIT " + DRAIN_LIMIT));
    if (pending == null || pending.isEmpty()) {
      return;
    }
    for (OutboxEvent event : pending) {
      if (event.getId() != null) {
        drainOne(event.getId());
      }
    }
  }

  private void drainOne(Long eventId) {
    OutboxEvent event = outboxEventMapper.selectById(eventId);
    if (event == null
        || event.getStatus() == null
        || event.getStatus() != STATUS_PENDING
        || !EVENT_TYPE.equals(event.getEventType())) {
      return;
    }
    try {
      NotifyPayload payload = objectMapper.readValue(event.getPayloadJson(), NotifyPayload.class);
      if (payload != null && payload.recipientId() != null) {
        notificationClient.ingest(
            internalToken,
            new NotificationClient.IngestRequest(
                payload.recipientId(),
                payload.type(),
                payload.actorId(),
                payload.aggregateKey(),
                payload.postId(),
                payload.commentId(),
                payload.preview()));
      }
      event.setStatus(STATUS_SENT);
      event.setSentAt(LocalDateTime.now());
      outboxEventMapper.updateById(event);
    } catch (JsonProcessingException ex) {
      log.error("notify outbox 无法解析 id={}", eventId, ex);
      event.setStatus(STATUS_FAILED);
      outboxEventMapper.updateById(event);
    } catch (RuntimeException ex) {
      log.warn("notify 投递失败，稍后重试 id={}", eventId, ex);
    }
  }

  public record NotifyPayload(
      Long recipientId,
      String type,
      long actorId,
      String aggregateKey,
      Long postId,
      Long commentId,
      String preview) {}
}
