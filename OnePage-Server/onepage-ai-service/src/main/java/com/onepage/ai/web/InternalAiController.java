package com.onepage.ai.web;

import com.onepage.ai.dto.AiDtos;
import com.onepage.ai.service.AiSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运营经 moderation Feign 读写配置。 */
@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {
  private final AiSettings aiSettings;
  private final InternalTokenFilter tokenFilter;

  public InternalAiController(AiSettings aiSettings, InternalTokenFilter tokenFilter) {
    this.aiSettings = aiSettings;
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
}
