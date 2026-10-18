package com.onepage.live.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 轮询推流是否还在；宽限期到了就关场。 */
@Component
public class LiveUnpublishJob {
  private final LiveService liveService;

  public LiveUnpublishJob(LiveService liveService) {
    this.liveService = liveService;
  }

  @Scheduled(fixedDelay = 2000L)
  public void flush() {
    liveService.watchIngest();
    liveService.flushUnpublished();
  }
}
