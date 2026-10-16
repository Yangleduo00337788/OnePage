package com.onepage.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.ai.AiConstants;
import com.onepage.ai.dto.AiDtos;
import com.onepage.common.api.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 兼容 OpenAI 的 {@code POST /chat/completions}。地址和密钥读运营配置。 */
@Component
public class LlmClient {
  private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10L);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(50L);

  private final ObjectMapper objectMapper;
  private final AiSettings aiSettings;
  private final SimpleClientHttpRequestFactory requestFactory;

  public LlmClient(ObjectMapper objectMapper, AiSettings aiSettings) {
    this.objectMapper = objectMapper;
    this.aiSettings = aiSettings;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    this.requestFactory = factory;
  }

  public boolean configured() {
    return StringUtils.hasText(aiSettings.current().llmApiKey());
  }

  public String complete(String system, String user) {
    AiSettings.Stored stored = aiSettings.current();
    return complete(stored.llmBaseUrl(), stored.llmApiKey(), stored.llmModel(), system, user);
  }

  public AiDtos.OpsProbe probe(String baseUrl, String apiKey, String model) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
      return new AiDtos.OpsProbe(false, AiConstants.MSG_PROBE_NO_KEY);
    }
    try {
      complete(baseUrl, apiKey, model, AiConstants.PROBE_SYSTEM, AiConstants.PROBE_USER);
      return new AiDtos.OpsProbe(true, AiConstants.MSG_PROBE_OK);
    } catch (Exception ex) {
      log.warn("llm probe failed");
      return new AiDtos.OpsProbe(false, AiConstants.MSG_PROBE_FAIL);
    }
  }

  public String complete(String baseUrl, String apiKey, String model, String system, String user) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
      throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
    }
    Map<String, Object> body = Map.of(
        "model", model,
        "temperature", 0.4,
        "messages", List.of(
            Map.of("role", "system", "content", system),
            Map.of("role", "user", "content", user)));
    try {
      String raw = RestClient.builder()
          .baseUrl(baseUrl)
          .requestFactory(requestFactory)
          .defaultHeader("Authorization", "Bearer " + apiKey)
          .build()
          .post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(String.class);
      JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      if (!content.isTextual() || content.asText().isBlank()) {
        throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
      }
      return content.asText().trim();
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("llm complete failed", ex);
      throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
    }
  }
}
