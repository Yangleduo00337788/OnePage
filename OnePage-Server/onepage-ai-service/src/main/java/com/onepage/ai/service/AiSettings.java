package com.onepage.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.ai.AiConstants;
import com.onepage.ai.dto.AiDtos;
import com.onepage.common.api.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 运营改的 AI 配置落 Redis；没写过则用环境变量。密钥不进日志。 */
@Service
public class AiSettings {
  private static final Logger log = LoggerFactory.getLogger(AiSettings.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final String envBaseUrl;
  private final String envApiKey;
  private final String envModel;

  public AiSettings(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      @Value("${onepage.llm-base-url}") String envBaseUrl,
      @Value("${onepage.llm-api-key:}") String envApiKey,
      @Value("${onepage.llm-model}") String envModel) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.envBaseUrl = envBaseUrl == null ? "" : envBaseUrl.trim();
    this.envApiKey = envApiKey == null ? "" : envApiKey.trim();
    this.envModel = envModel == null ? "" : envModel.trim();
  }

  public Stored current() {
    Stored stored = readStored();
    return stored == null ? fromEnv() : stored;
  }

  public AiDtos.Bootstrap bootstrap() {
    Stored stored = current();
    return new AiDtos.Bootstrap(stored.yehPreset(), stored.yehQuestions());
  }

  public AiDtos.OpsView opsView() {
    Stored stored = current();
    return new AiDtos.OpsView(
        stored.llmBaseUrl(),
        stored.llmModel(),
        StringUtils.hasText(stored.llmApiKey()),
        mask(stored.llmApiKey()),
        stored.yehPreset(),
        stored.yehQuestions(),
        stored.chatEnabled(),
        stored.polishEnabled());
  }

  public AiDtos.OpsView save(AiDtos.OpsPatch patch) {
    Stored prev = current();
    String baseUrl = patch == null || !StringUtils.hasText(patch.llmBaseUrl())
        ? prev.llmBaseUrl()
        : trimSlash(patch.llmBaseUrl());
    String model = patch == null || !StringUtils.hasText(patch.llmModel())
        ? prev.llmModel()
        : patch.llmModel().trim();
    String apiKey = prev.llmApiKey();
    if (patch != null && patch.llmApiKey() != null && StringUtils.hasText(patch.llmApiKey().trim())) {
      apiKey = patch.llmApiKey().trim();
    }
    if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
      throw BusinessException.validation("模型地址和模型名必填");
    }
    String preset = patch == null || !StringUtils.hasText(patch.yehPreset())
        ? prev.yehPreset()
        : patch.yehPreset().trim();
    List<String> questions = patch == null ? prev.yehQuestions() : cleanQuestions(patch.yehQuestions());
    if (questions.isEmpty()) {
      questions = prev.yehQuestions();
    }
    boolean chat = patch == null || patch.chatEnabled() == null ? prev.chatEnabled() : patch.chatEnabled();
    boolean polish = patch == null || patch.polishEnabled() == null ? prev.polishEnabled() : patch.polishEnabled();
    Stored next = new Stored(baseUrl, apiKey, model, preset, questions, chat, polish);
    try {
      redis.opsForValue().set(AiConstants.REDIS_SETTINGS_KEY, objectMapper.writeValueAsString(next));
    } catch (Exception ex) {
      log.warn("ai settings save failed");
      throw BusinessException.configMissing("暂时不能保存 AI 配置");
    }
    return opsView();
  }

  private Stored readStored() {
    try {
      String raw = redis.opsForValue().get(AiConstants.REDIS_SETTINGS_KEY);
      if (!StringUtils.hasText(raw)) {
        return null;
      }
      Stored stored = objectMapper.readValue(raw, Stored.class);
      if (stored == null) {
        return null;
      }
      List<String> questions = cleanQuestions(stored.yehQuestions());
      if (questions.isEmpty()) {
        questions = defaultQuestions();
      }
      String preset = StringUtils.hasText(stored.yehPreset()) ? stored.yehPreset().trim() : AiConstants.DEFAULT_YEH_PRESET;
      return new Stored(
          trimSlash(stored.llmBaseUrl()),
          stored.llmApiKey() == null ? "" : stored.llmApiKey().trim(),
          stored.llmModel() == null ? "" : stored.llmModel().trim(),
          preset,
          questions,
          stored.chatEnabled(),
          stored.polishEnabled());
    } catch (Exception ex) {
      log.warn("ai settings read failed, fallback env");
      return null;
    }
  }

  private Stored fromEnv() {
    return new Stored(
        trimSlash(envBaseUrl),
        envApiKey,
        envModel,
        AiConstants.DEFAULT_YEH_PRESET,
        defaultQuestions(),
        true,
        true);
  }

  private static List<String> defaultQuestions() {
    return AiConstants.DEFAULT_YEH_QUESTIONS;
  }

  private static List<String> cleanQuestions(List<String> raw) {
    List<String> out = new ArrayList<>();
    if (raw == null) {
      return out;
    }
    for (String item : raw) {
      if (!StringUtils.hasText(item)) {
        continue;
      }
      out.add(item.trim());
      if (out.size() >= AiConstants.QUESTIONS_MAX) {
        break;
      }
    }
    return out;
  }

  private static String trimSlash(String url) {
    String value = url == null ? "" : url.trim();
    if (value.endsWith("/")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static String mask(String key) {
    if (!StringUtils.hasText(key)) {
      return "";
    }
    String value = key.trim();
    if (value.length() <= 4) {
      return "****";
    }
    return "****" + value.substring(value.length() - 4);
  }

  @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
  public record Stored(
      String llmBaseUrl,
      String llmApiKey,
      String llmModel,
      String yehPreset,
      List<String> yehQuestions,
      boolean chatEnabled,
      boolean polishEnabled) {}
}
