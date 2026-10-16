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
        stored.polishEnabled(),
        readStored() != null,
        stored.chatSystem(),
        stored.polishSystem(),
        stored.continueSystem(),
        stored.retrieveLimit(),
        stored.contextBodyMax(),
        stored.temperature(),
        stored.maxTokens(),
        stored.streamEnabled());
  }

  public Stored overlay(AiDtos.OpsPatch patch) {
    Stored prev = current();
    if (patch == null) {
      return prev;
    }
    String baseUrl = StringUtils.hasText(patch.llmBaseUrl()) ? trimSlash(patch.llmBaseUrl()) : prev.llmBaseUrl();
    String model = StringUtils.hasText(patch.llmModel()) ? patch.llmModel().trim() : prev.llmModel();
    String apiKey = prev.llmApiKey();
    if (Boolean.TRUE.equals(patch.clearLlmKey())) {
      apiKey = "";
    } else if (StringUtils.hasText(patch.llmApiKey())) {
      apiKey = patch.llmApiKey().trim();
    }
    String preset = StringUtils.hasText(patch.yehPreset())
        ? patch.yehPreset().trim()
        : prev.yehPreset();
    List<String> questions = patch.yehQuestions() == null
        ? prev.yehQuestions()
        : cleanQuestions(patch.yehQuestions());
    if (questions.isEmpty()) {
      questions = defaultQuestions();
    }
    boolean chat = patch.chatEnabled() == null ? prev.chatEnabled() : patch.chatEnabled();
    boolean polish = patch.polishEnabled() == null ? prev.polishEnabled() : patch.polishEnabled();
    String chatSystem = overlayPrompt(patch.chatSystem(), prev.chatSystem(), AiConstants.CHAT_SYSTEM);
    String polishSystem = overlayPrompt(
        patch.polishSystem(), prev.polishSystem(), AiConstants.POLISH_SYSTEM);
    String continueSystem = overlayPrompt(
        patch.continueSystem(), prev.continueSystem(), AiConstants.CONTINUE_SYSTEM);
    int retrieveLimit = patch.retrieveLimit() == null
        ? prev.retrieveLimit()
        : clamp(
            patch.retrieveLimit(),
            AiConstants.RETRIEVE_LIMIT,
            1,
            AiConstants.RETRIEVE_MAX);
    int contextBodyMax = patch.contextBodyMax() == null
        ? prev.contextBodyMax()
        : clamp(
            patch.contextBodyMax(),
            AiConstants.CONTEXT_BODY_MAX,
            50,
            AiConstants.CONTEXT_BODY_CAP);
    double temperature = patch.temperature() == null
        ? clampTemp(prev.temperature())
        : clampTemp(patch.temperature());
    int maxTokens = patch.maxTokens() == null
        ? clampMaxTokens(prev.maxTokens())
        : clampMaxTokens(patch.maxTokens());
    boolean stream = patch.streamEnabled() == null
        ? Boolean.TRUE.equals(prev.streamEnabled())
        : patch.streamEnabled();
    return new Stored(
        baseUrl,
        apiKey,
        model,
        preset,
        questions,
        chat,
        polish,
        chatSystem,
        polishSystem,
        continueSystem,
        retrieveLimit,
        contextBodyMax,
        temperature,
        maxTokens,
        stream);
  }

  public AiDtos.OpsView save(AiDtos.OpsPatch patch) {
    Stored next = overlay(patch);
    String baseUrl = next.llmBaseUrl();
    String model = next.llmModel();
    if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
      throw BusinessException.validation("模型地址和模型名必填");
    }
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
      throw BusinessException.validation("模型地址须以 http:// 或 https:// 开头");
    }
    try {
      redis.opsForValue().set(AiConstants.REDIS_SETTINGS_KEY, objectMapper.writeValueAsString(next));
    } catch (Exception ex) {
      log.warn("ai settings save failed");
      throw BusinessException.configMissing("暂时不能保存 AI 配置");
    }
    return opsView();
  }

  public AiDtos.OpsView reset() {
    redis.delete(AiConstants.REDIS_SETTINGS_KEY);
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
          stored.polishEnabled(),
          readPrompt(stored.chatSystem(), AiConstants.CHAT_SYSTEM),
          readPrompt(stored.polishSystem(), AiConstants.POLISH_SYSTEM),
          readPrompt(stored.continueSystem(), AiConstants.CONTINUE_SYSTEM),
          clamp(stored.retrieveLimit(), AiConstants.RETRIEVE_LIMIT, 1, AiConstants.RETRIEVE_MAX),
          clamp(
              stored.contextBodyMax(),
              AiConstants.CONTEXT_BODY_MAX,
              50,
              AiConstants.CONTEXT_BODY_CAP),
          clampTemp(stored.temperature()),
          clampMaxTokens(stored.maxTokens()),
          Boolean.TRUE.equals(stored.streamEnabled()));
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
        true,
        AiConstants.CHAT_SYSTEM,
        AiConstants.POLISH_SYSTEM,
        AiConstants.CONTINUE_SYSTEM,
        AiConstants.RETRIEVE_LIMIT,
        AiConstants.CONTEXT_BODY_MAX,
        AiConstants.TEMPERATURE_DEFAULT,
        0,
        false);
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

  private static String overlayPrompt(String patch, String prev, String fallback) {
    if (patch == null) {
      return promptOrDefault(prev, fallback);
    }
    return promptOrDefault(patch, fallback);
  }

  private static String promptOrDefault(String value, String fallback) {
    if (!StringUtils.hasText(value)) {
      return fallback;
    }
    String text = value.trim();
    if (text.length() > AiConstants.PROMPT_MAX) {
      throw BusinessException.validation("系统提示词过长");
    }
    return text;
  }

  private static String readPrompt(String value, String fallback) {
    if (!StringUtils.hasText(value)) {
      return fallback;
    }
    String text = value.trim();
    if (text.length() > AiConstants.PROMPT_MAX) {
      return text.substring(0, AiConstants.PROMPT_MAX);
    }
    return text;
  }

  private static int clamp(int value, int fallback, int min, int max) {
    if (value < min) {
      return fallback;
    }
    return Math.min(value, max);
  }

  private static double clampTemp(Double value) {
    if (value == null || value.isNaN() || value < 0d) {
      return AiConstants.TEMPERATURE_DEFAULT;
    }
    return Math.min(value, AiConstants.TEMPERATURE_MAX);
  }

  private static int clampMaxTokens(Integer value) {
    if (value == null || value < 0) {
      return 0;
    }
    return Math.min(value, AiConstants.MAX_TOKENS_CAP);
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
      boolean polishEnabled,
      String chatSystem,
      String polishSystem,
      String continueSystem,
      int retrieveLimit,
      int contextBodyMax,
      Double temperature,
      Integer maxTokens,
      Boolean streamEnabled) {}
}
