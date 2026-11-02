package com.onepage.content.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 把未写入曝光表的 feed.cover_shown 补上。进程重启后也能扫到 outbox。 */
@Component
public class CoverShowRelay {
  private final CoverShowRecorder coverShowRecorder;

  public CoverShowRelay(CoverShowRecorder coverShowRecorder) {
    this.coverShowRecorder = coverShowRecorder;
  }

  @Scheduled(fixedDelay = 1000L, initialDelay = 5000L)
  public void flush() {
    coverShowRecorder.drainPending();
  }
}
