package com.onepage.moderation.client;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/** 探测连通要等上游 LLM，单独加长读超时。不要标 @Configuration，避免套到别的 Feign。 */
public class AiFeignConfig {
  private static final int CONNECT_MS = 10_000;
  private static final int READ_MS = 55_000;

  @Bean
  public Request.Options aiRequestOptions() {
    return new Request.Options(CONNECT_MS, TimeUnit.MILLISECONDS, READ_MS, TimeUnit.MILLISECONDS, true);
  }
}
