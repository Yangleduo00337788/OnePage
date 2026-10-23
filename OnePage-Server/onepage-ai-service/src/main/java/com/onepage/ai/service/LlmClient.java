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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

  public boolean reviewConfigured() {
    return AiSettings.textConfigured(aiSettings.current());
  }

  public boolean visionConfigured() {
    return AiSettings.visionConfigured(aiSettings.current());
  }

  public String complete(String system, String user) {
    return completeChat(system, user, List.of());
  }

  public String completeChat(String system, String user, List<String> imageDataUrls) {
    AiSettings.Stored stored = aiSettings.current();
    Object content = userContent(user, imageDataUrls);
    if (imageDataUrls != null && !imageDataUrls.isEmpty()) {
      return complete(
          stored.visionLlmBaseUrl(),
          stored.visionLlmApiKey(),
          stored.visionLlmModel(),
          system,
          content,
          null,
          null);
    }
    return complete(
        stored.llmBaseUrl(), stored.llmApiKey(), stored.llmModel(), system, content, null, null);
  }

  public String completeReview(String system, String user) {
    AiSettings.Stored stored = aiSettings.current();
    return complete(
        stored.llmBaseUrl(),
        stored.llmApiKey(),
        stored.llmModel(),
        system,
        user,
        AiConstants.REVIEW_TEMPERATURE,
        AiConstants.REVIEW_MAX_TOKENS);
  }

  public void completeStream(String system, String user, Consumer<String> onDelta) {
    AiSettings.Stored stored = aiSettings.current();
    completeStream(
        stored.llmBaseUrl(), stored.llmApiKey(), stored.llmModel(), system, user, onDelta);
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
    return complete(baseUrl, apiKey, model, system, user, null, null);
  }

  public String complete(
      String baseUrl,
      String apiKey,
      String model,
      String system,
      String user,
      Double temperature,
      Integer maxTokens) {
    return complete(baseUrl, apiKey, model, system, (Object) user, temperature, maxTokens);
  }

  public String complete(
      String baseUrl,
      String apiKey,
      String model,
      String system,
      Object userContent,
      Double temperature,
      Integer maxTokens) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
      throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
    }
    Map<String, Object> body = chatBody(model, system, userContent, false, temperature, maxTokens);
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
      String answer = messageText(root.path("choices").path(0).path("message").path("content"));
      if (!StringUtils.hasText(answer)) {
        throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
      }
      return answer;
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("llm complete failed", ex);
      throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
    }
  }

  public void completeStream(String system, String user, List<String> imageDataUrls, Consumer<String> onDelta) {
    AiSettings.Stored stored = aiSettings.current();
    Object content = userContent(user, imageDataUrls);
    if (imageDataUrls != null && !imageDataUrls.isEmpty()) {
      completeStream(
          stored.visionLlmBaseUrl(),
          stored.visionLlmApiKey(),
          stored.visionLlmModel(),
          system,
          content,
          onDelta);
      return;
    }
    completeStream(
        stored.llmBaseUrl(), stored.llmApiKey(), stored.llmModel(), system, content, onDelta);
  }

  public void completeStream(
      String baseUrl,
      String apiKey,
      String model,
      String system,
      String user,
      Consumer<String> onDelta) {
    completeStream(baseUrl, apiKey, model, system, (Object) user, onDelta);
  }

  public void completeStream(
      String baseUrl,
      String apiKey,
      String model,
      String system,
      Object userContent,
      Consumer<String> onDelta) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
      throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
    }
    Map<String, Object> body = chatBody(model, system, userContent, true, null, null);
    try {
      RestClient.builder()
          .baseUrl(baseUrl)
          .requestFactory(requestFactory)
          .defaultHeader("Authorization", "Bearer " + apiKey)
          .build()
          .post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM)
          .body(body)
          .exchange((request, response) -> {
            if (response.getStatusCode().isError()) {
              throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
            }
            try (BufferedReader reader =
                new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
              String line;
              while ((line = reader.readLine()) != null) {
                String payload = sseData(line);
                if (payload == null) {
                  continue;
                }
                if ("[DONE]".equals(payload)) {
                  break;
                }
                String piece = deltaContent(payload);
                if (StringUtils.hasText(piece)) {
                  onDelta.accept(piece);
                }
              }
            }
            return null;
          });
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("llm stream failed", ex);
      throw BusinessException.configMissing(AiConstants.MSG_NO_ANSWER);
    }
  }

  private static Object userContent(String user, List<String> imageDataUrls) {
    if (imageDataUrls == null || imageDataUrls.isEmpty()) {
      return user == null ? "" : user;
    }
    List<Map<String, Object>> parts = new ArrayList<>();
    Map<String, Object> text = new LinkedHashMap<>();
    text.put("type", "text");
    text.put("text", user == null ? "" : user);
    parts.add(text);
    for (String dataUrl : imageDataUrls) {
      if (!StringUtils.hasText(dataUrl)) {
        continue;
      }
      Map<String, Object> image = new LinkedHashMap<>();
      image.put("type", "image_url");
      image.put("image_url", Map.of("url", dataUrl));
      parts.add(image);
    }
    return parts;
  }

  private Map<String, Object> chatBody(
      String model,
      String system,
      Object userContent,
      boolean stream,
      Double temperature,
      Integer maxTokens) {
    AiSettings.Stored stored = aiSettings.current();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    double temp = temperature != null
        ? temperature
        : (stored.temperature() == null ? AiConstants.TEMPERATURE_DEFAULT : stored.temperature());
    body.put("temperature", temp);
    int tokens = maxTokens != null
        ? maxTokens
        : (stored.maxTokens() == null ? 0 : stored.maxTokens());
    if (tokens > 0) {
      body.put("max_tokens", tokens);
    }
    body.put("stream", stream);
    body.put(
        "messages",
        List.of(
            Map.of("role", "system", "content", system == null ? "" : system),
            Map.of("role", "user", "content", userContent == null ? "" : userContent)));
    return body;
  }

  private static String sseData(String line) {
    if (!StringUtils.hasText(line) || !line.startsWith("data:")) {
      return null;
    }
    return line.substring(5).trim();
  }

  private static String messageText(JsonNode content) {
    if (content == null || content.isMissingNode() || content.isNull()) {
      return "";
    }
    if (content.isTextual()) {
      return content.asText().trim();
    }
    if (!content.isArray()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (JsonNode part : content) {
      if (part.isTextual()) {
        sb.append(part.asText());
      } else if (part.path("text").isTextual()) {
        sb.append(part.path("text").asText());
      }
    }
    return sb.toString().trim();
  }

  private String deltaContent(String payload) {
    try {
      JsonNode choice = objectMapper.readTree(payload).path("choices").path(0);
      JsonNode content = choice.path("delta").path("content");
      if (content.isTextual()) {
        return content.asText();
      }
      JsonNode text = choice.path("text");
      return text.isTextual() ? text.asText() : "";
    } catch (Exception ex) {
      return "";
    }
  }
}
