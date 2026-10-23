package com.onepage.ai.web;

import com.onepage.ai.dto.AiDtos;
import com.onepage.ai.service.AiService;
import com.onepage.ai.service.AiSettings;
import com.onepage.ai.service.LlmClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运营经 moderation Feign 读写配置。不解析 Cookie。 */
@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {
  private final AiSettings aiSettings;
  private final AiService aiService;
  private final LlmClient llmClient;
  private final InternalTokenFilter tokenFilter;

  public InternalAiController(
      AiSettings aiSettings,
      AiService aiService,
      LlmClient llmClient,
      InternalTokenFilter tokenFilter) {
    this.aiSettings = aiSettings;
    this.aiService = aiService;
    this.llmClient = llmClient;
    this.tokenFilter = tokenFilter;
  }

  @GetMapping("/config")
  public AiDtos.OpsView get(HttpServletRequest request) {
    tokenFilter.assertInternal(request);
    return aiSettings.opsView();
  }

  @PostMapping("/config")
  public AiDtos.OpsView save(HttpServletRequest request, @RequestBody AiDtos.OpsPatch patch) {
    tokenFilter.assertInternal(request);
    return aiSettings.save(patch);
  }

  @PostMapping("/config/reset")
  public AiDtos.OpsView reset(HttpServletRequest request) {
    tokenFilter.assertInternal(request);
    return aiSettings.reset();
  }

  @PostMapping("/review")
  public AiDtos.ReviewResult review(
      HttpServletRequest request, @RequestBody(required = false) AiDtos.ReviewSpec spec) {
    tokenFilter.assertInternal(request);
    return aiService.review(spec);
  }

  @PostMapping("/probe")
  public AiDtos.OpsProbe probe(
      HttpServletRequest request, @RequestBody(required = false) AiDtos.OpsPatch patch) {
    tokenFilter.assertInternal(request);
    AiSettings.Stored stored = aiSettings.overlay(patch);
    return llmClient.probe(stored.llmBaseUrl(), stored.llmApiKey(), stored.llmModel());
  }

  @PostMapping("/vision/probe")
  public AiDtos.OpsProbe visionProbe(
      HttpServletRequest request, @RequestBody(required = false) AiDtos.OpsPatch patch) {
    tokenFilter.assertInternal(request);
    AiSettings.Stored stored = aiSettings.overlay(patch);
    return llmClient.probe(
        stored.visionLlmBaseUrl(), stored.visionLlmApiKey(), stored.visionLlmModel());
  }
}
