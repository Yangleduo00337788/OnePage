package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.entity.OutboxEvent;
import com.onepage.content.mapper.OutboxEventMapper;
import com.onepage.content.mapper.PostCoverShowMapper;
import com.onepage.content.mapper.PostCoverShowMapper.ShowRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 发现流曝光先写入 outbox_event，再写入 post_cover_show。不接 RocketMQ。
 */
@Service
public class CoverShowRecorder {
  static final String AGGREGATE_TYPE = "feed";
  static final String EVENT_TYPE = "feed.cover_shown";
  private static final int STATUS_PENDING = 0;
  private static final int STATUS_SENT = 1;
  private static final int STATUS_FAILED = 2;
  private static final int DRAIN_LIMIT = 40;
  private static final Logger log = LoggerFactory.getLogger(CoverShowRecorder.class);

  private final OutboxEventMapper outboxEventMapper;
  private final PostCoverShowMapper coverShowMapper;
  private final ObjectMapper objectMapper;
  private final Executor coverShowExecutor;

  public CoverShowRecorder(
      OutboxEventMapper outboxEventMapper,
      PostCoverShowMapper coverShowMapper,
      ObjectMapper objectMapper,
      @Qualifier("coverShowExecutor") Executor coverShowExecutor) {
    this.outboxEventMapper = outboxEventMapper;
    this.coverShowMapper = coverShowMapper;
    this.objectMapper = objectMapper;
    this.coverShowExecutor = coverShowExecutor;
  }

  public void enqueue(List<ShowRow> shows) {
    if (shows == null || shows.isEmpty()) {
      return;
    }
    List<ShowRow> snapshot = List.copyOf(shows);
    OutboxEvent event = new OutboxEvent();
    long eventId = IdGenerator.nextId();
    event.setId(eventId);
    event.setAggregateType(AGGREGATE_TYPE);
    event.setAggregateId(String.valueOf(eventId));
    event.setEventType(EVENT_TYPE);
    event.setStatus(STATUS_PENDING);
    event.setCreatedAt(LocalDateTime.now());
    try {
      event.setPayloadJson(objectMapper.writeValueAsString(new CoverShowPayload(snapshot)));
    } catch (JsonProcessingException ex) {
      log.error("cover show outbox 序列化失败 size={}", snapshot.size(), ex);
      return;
    }
    outboxEventMapper.insert(event);
    try {
      coverShowExecutor.execute(() -> drainOne(eventId));
    } catch (RejectedExecutionException ignored) {
      // 队列满时由定时任务补写，outbox 已落盘
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
      CoverShowPayload payload = objectMapper.readValue(event.getPayloadJson(), CoverShowPayload.class);
      List<ShowRow> rows = payload == null ? List.of() : payload.rows();
      if (rows != null && !rows.isEmpty()) {
        coverShowMapper.insertBatch(rows);
      }
      event.setStatus(STATUS_SENT);
      event.setSentAt(LocalDateTime.now());
      outboxEventMapper.updateById(event);
    } catch (JsonProcessingException ex) {
      log.error("cover show outbox 无法解析 id={}", eventId, ex);
      event.setStatus(STATUS_FAILED);
      outboxEventMapper.updateById(event);
    } catch (RuntimeException ex) {
      log.warn("cover show 写入失败，稍后重试 id={}", eventId, ex);
    }
  }

  public record CoverShowPayload(List<ShowRow> rows) {}
}
