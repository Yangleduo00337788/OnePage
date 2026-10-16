package com.onepage.ai.web;

import com.onepage.ai.dto.AiDtos;
import com.onepage.ai.service.AiService;
import com.onepage.ai.service.AiSettings;
import com.onepage.common.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 发帖润色与耶耶问答。只认网关 {@code X-User-Id}。首页文案公开。 */
@RestController
@RequestMapping("/api/v1")
public class AiController {
  private final AiService aiService;
  private final AiSettings aiSettings;

  public AiController(AiService aiService, AiSettings aiSettings) {
    this.aiService = aiService;
    this.aiSettings = aiSettings;
  }

  @GetMapping("/ai/bootstrap")
  public AiDtos.Bootstrap bootstrap() {
    return aiSettings.bootstrap();
  }

  @PostMapping("/ai/posts/polish")
  public AiDtos.PolishResult polish(HttpServletRequest request, @RequestBody AiDtos.PolishSpec spec) {
    CurrentUser.requireUserId(request);
    return aiService.polish(spec);
  }

  @PostMapping("/ai/chat")
  public AiDtos.ChatResult chat(HttpServletRequest request, @RequestBody AiDtos.ChatSpec spec) {
    CurrentUser.requireUserId(request);
    return aiService.chat(spec);
  }
}
