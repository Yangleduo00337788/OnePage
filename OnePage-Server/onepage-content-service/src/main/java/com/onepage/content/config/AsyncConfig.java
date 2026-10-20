package com.onepage.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** 发帖机审不挡发布接口。 */
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
}
