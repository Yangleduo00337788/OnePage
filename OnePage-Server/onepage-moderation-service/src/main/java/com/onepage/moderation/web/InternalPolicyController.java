package com.onepage.moderation.web;

import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.service.CommunityPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户/内容服务拉社区规则。 */
@RestController
@RequestMapping("/internal")
public class InternalPolicyController {
  private final CommunityPolicyService communityPolicyService;
  private final InternalTokenFilter tokenFilter;

  public InternalPolicyController(
      CommunityPolicyService communityPolicyService, InternalTokenFilter tokenFilter) {
    this.communityPolicyService = communityPolicyService;
    this.tokenFilter = tokenFilter;
  }

  @GetMapping("/policy")
  public ModerationDtos.CommunityPolicyView policy(HttpServletRequest request) {
    tokenFilter.assertInternal(request);
    return communityPolicyService.view();
  }
}
