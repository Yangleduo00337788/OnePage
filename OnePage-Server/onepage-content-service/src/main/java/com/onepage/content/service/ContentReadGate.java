package com.onepage.content.service;

import com.onepage.common.api.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * 限制同时跑发现流和笔记详情的个数，把连接池留给评论和发帖。
 * 名额满了立刻「服务繁忙」，不要占着连接排队。
 */
@Component
public class ContentReadGate {
  private final Semaphore slots;

  public ContentReadGate(@Value("${onepage.feed-read-permits:16}") int permits) {
    int n = Math.max(1, permits);
    this.slots = new Semaphore(n);
  }

  public <T> T call(Supplier<T> action) {
    if (!slots.tryAcquire()) {
      throw BusinessException.rateLimited("服务繁忙，请稍后重试");
    }
    try {
      return action.get();
    } finally {
      slots.release();
    }
  }
}
