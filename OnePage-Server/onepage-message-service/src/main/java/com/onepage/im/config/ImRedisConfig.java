package com.onepage.im.config;

import com.onepage.im.service.ImPushHub;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 订阅 {@link ImPushHub#CHANNEL}，把别的实例发出的帧送到本机 WebSocket。 */
@Configuration
public class ImRedisConfig {
  @Bean
  public RedisMessageListenerContainer imPushContainer(
      RedisConnectionFactory factory, ImPushHub hub) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener(hub, new ChannelTopic(ImPushHub.CHANNEL));
    return container;
  }
}
