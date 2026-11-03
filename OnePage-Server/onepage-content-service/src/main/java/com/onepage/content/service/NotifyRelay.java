package com.onepage.content.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 把未投递的 notify.ingest 补上。进程重启后也能扫到 outbox。 */
@Component
public class NotifyRelay {
  private final NotifyOutboxRecorder notifyOutboxRecorder;

  public NotifyRelay(NotifyOutboxRecorder notifyOutboxRecorder) {
    this.notifyOutboxRecorder = notifyOutboxRecorder;
  }

  @Scheduled(fixedDelay = 1000L, initialDelay = 5000L)
  public void flush() {
    notifyOutboxRecorder.drainPending();
  }
}
