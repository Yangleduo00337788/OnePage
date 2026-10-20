package com.onepage.ai.dto;

import java.util.List;

/** 对外 JSON，对齐契约。 */
public final class AiDtos {
  private AiDtos() {}

  /** {@code mode}：{@code optimize} 优化标题正文；{@code continue} 续写正文。缺省 optimize。 */
  public record PolishSpec(String title, String body, String mode) {}

  public record PolishResult(String title, String body) {}

  public record ChatSpec(String question, String sessionId) {}

  public record Citation(
      String postId, String snippet, String title, String coverUrl, String nickname, String pageId) {}

  public record ChatResult(String answer, List<Citation> citations) {}

  public record Bootstrap(String yehPreset, List<String> yehQuestions) {}

  public record OpsView(
      String llmBaseUrl,
      String llmModel,
      boolean llmKeySet,
      String llmKeyMasked,
      String yehPreset,
      List<String> yehQuestions,
      boolean chatEnabled,
      boolean polishEnabled,
      boolean persisted,
      String chatSystem,
      String polishSystem,
      String continueSystem,
      int retrieveLimit,
      int contextBodyMax,
      double temperature,
      int maxTokens,
      boolean streamEnabled,
      boolean reviewEnabled,
      String reviewSystem,
      String reviewLlmBaseUrl,
      String reviewLlmModel,
      boolean reviewLlmKeySet,
      String reviewLlmKeyMasked,
      int reviewRiskLimit) {}

  public record OpsPatch(
      String llmBaseUrl,
      String llmApiKey,
      String llmModel,
      String yehPreset,
      List<String> yehQuestions,
      Boolean chatEnabled,
      Boolean polishEnabled,
      Boolean clearLlmKey,
      String chatSystem,
      String polishSystem,
      String continueSystem,
      Integer retrieveLimit,
      Integer contextBodyMax,
      Double temperature,
      Integer maxTokens,
      Boolean streamEnabled,
      Boolean reviewEnabled,
      String reviewSystem,
      String reviewLlmBaseUrl,
      String reviewLlmApiKey,
      String reviewLlmModel,
      Boolean clearReviewLlmKey,
      Integer reviewRiskLimit) {}

  public record OpsProbe(boolean ok, String message) {}

  public record StreamDelta(String t) {}

  public record StreamDone(List<Citation> citations) {}

  public record StreamError(String message) {}

  public record StreamStage(String s, int n) {}

  public record ReviewSpec(String postId, String title, String body) {}

  public record ReviewResult(String verdict, String reason, Integer score) {}
}
