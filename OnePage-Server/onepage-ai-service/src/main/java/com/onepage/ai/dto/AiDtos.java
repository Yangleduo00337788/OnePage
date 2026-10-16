package com.onepage.ai.dto;

import java.util.List;

/** 对外 JSON，对齐契约。 */
public final class AiDtos {
  private AiDtos() {}

  /** {@code mode}：{@code optimize} 优化标题正文；{@code continue} 续写正文。缺省 optimize。 */
  public record PolishSpec(String title, String body, String mode) {}

  public record PolishResult(String title, String body) {}

  public record ChatSpec(String question, String sessionId) {}

  public record Citation(String postId, String snippet) {}

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
      boolean persisted) {}

  public record OpsPatch(
      String llmBaseUrl,
      String llmApiKey,
      String llmModel,
      String yehPreset,
      List<String> yehQuestions,
      Boolean chatEnabled,
      Boolean polishEnabled,
      Boolean clearLlmKey) {}

  public record OpsProbe(boolean ok, String message) {}
}
