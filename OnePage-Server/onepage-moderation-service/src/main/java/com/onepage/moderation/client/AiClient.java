package com.onepage.moderation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/** 运营 AI 配置落在 ai-service Redis。 */
@FeignClient(name = "onepage-ai-service", configuration = AiFeignConfig.class)
public interface AiClient {
  @GetMapping("/internal/ai/config")
  OpsAiView get(@RequestHeader("X-Internal-Token") String token);

  @PostMapping("/internal/ai/config")
  OpsAiView save(@RequestHeader("X-Internal-Token") String token, @RequestBody OpsAiPatch body);

  @PostMapping("/internal/ai/config/reset")
  OpsAiView reset(@RequestHeader("X-Internal-Token") String token);

  @PostMapping("/internal/ai/probe")
  OpsAiProbe probe(@RequestHeader("X-Internal-Token") String token, @RequestBody OpsAiPatch body);

  record OpsAiView(
      String llmBaseUrl,
      String llmModel,
      boolean llmKeySet,
      String llmKeyMasked,
      String yehPreset,
      List<String> yehQuestions,
      boolean chatEnabled,
      boolean polishEnabled,
      boolean persisted) {}

  record OpsAiPatch(
      String llmBaseUrl,
      String llmApiKey,
      String llmModel,
      String yehPreset,
      List<String> yehQuestions,
      Boolean chatEnabled,
      Boolean polishEnabled,
      Boolean clearLlmKey) {}

  record OpsAiProbe(boolean ok, String message) {}
}
