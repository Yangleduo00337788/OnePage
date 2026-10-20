package com.onepage.user.service;

import com.onepage.common.api.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 昵称 / 签名长度与违禁词。上限和词表来自运营配置。 */
@Component
public class ProfileTextGuard {
  private final CommunityPolicyCache policyCache;

  public ProfileTextGuard(CommunityPolicyCache policyCache) {
    this.policyCache = policyCache;
  }

  public String nickname(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw BusinessException.validation("昵称不能为空");
    }
    String text = raw.trim();
    int max = policyCache.current().nicknameMax();
    if (text.length() > max) {
      throw BusinessException.validation("昵称不能超过 " + max + " 字");
    }
    assertClean(text);
    return text;
  }

  public String bio(String raw) {
    if (raw == null) {
      return null;
    }
    String text = raw.trim();
    int max = policyCache.current().bioMax();
    if (text.length() > max) {
      throw BusinessException.validation("签名不能超过 " + max + " 字");
    }
    if (text.isEmpty()) {
      return "";
    }
    assertClean(text);
    return text;
  }

  private void assertClean(String text) {
    policyCache.assertClean(text);
  }
}
