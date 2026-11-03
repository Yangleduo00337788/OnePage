package com.onepage.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** 发帖机审不挡发布。曝光和通知加速写入已落盘的 outbox，满了交给定时任务，不许丢任务。 */
@Configuration
@EnableAsync
public class AsyncConfig {
  @Bean(name = "postReviewExecutor")
  public Executor postReviewExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(2);
    pool.setMaxPoolSize(4);
    pool.setQueueCapacity(200);
    pool.setThreadNamePrefix("post-review-");
    pool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
    pool.initialize();
    return pool;
  }

  @Bean(name = "coverShowExecutor")
  public Executor coverShowExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(2);
    pool.setMaxPoolSize(4);
    pool.setQueueCapacity(500);
    pool.setThreadNamePrefix("cover-show-");
    pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    pool.initialize();
    return pool;
  }

  @Bean(name = "notifyExecutor")
  public Executor notifyExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(2);
    pool.setMaxPoolSize(4);
    pool.setQueueCapacity(200);
    pool.setThreadNamePrefix("notify-");
    pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    pool.initialize();
    return pool;
  }
}
