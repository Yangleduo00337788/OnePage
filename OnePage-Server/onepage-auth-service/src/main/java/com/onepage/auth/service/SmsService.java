package com.onepage.auth.service;

import com.onepage.common.api.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 短信验证码。{@code SMS_MOCK=true} 时不调运营商，验证码为 {@code SMS_MOCK_CODE}（默认开发用 888888）。
 */
@Service
public class SmsService {
  private final StringRedisTemplate redis;
  private final boolean mock;
  private final String mockCode;
  private final int cooldownSec;

  public SmsService(
      StringRedisTemplate redis,
      @Value("${onepage.sms.mock}") boolean mock,
      @Value("${onepage.sms.mock-code}") String mockCode,
      @Value("${onepage.sms.cooldown-sec}") int cooldownSec) {
    this.redis = redis;
    this.mock = mock;
    this.mockCode = mockCode;
    this.cooldownSec = cooldownSec;
  }

  /**
   * 写入 Redis 验证码。mock 模式下码为配置值，不调用短信网关。
   */
  public int send(String phone, String scene) {
    String coolKey = "sms:cool:" + scene + ":" + phone;
    if (Boolean.TRUE.equals(redis.hasKey(coolKey))) {
      throw BusinessException.rateLimited("验证码发送过于频繁");
    }
    String code = mock ? mockCode : String.valueOf(100000 + (int) (Math.random() * 900000));
    redis.opsForValue().set("sms:code:" + scene + ":" + phone, code, Duration.ofMinutes(5));
    redis.opsForValue().set(coolKey, "1", Duration.ofSeconds(cooldownSec));
    return cooldownSec;
  }

  /** 用过即删，防止同一码反复登录。 */
  public void verify(String phone, String scene, String code) {
    String key = "sms:code:" + scene + ":" + phone;
    String cached = redis.opsForValue().get(key);
    if (cached == null || !cached.equals(code)) {
      throw BusinessException.validation("验证码不正确或已过期");
    }
    redis.delete(key);
  }
}
