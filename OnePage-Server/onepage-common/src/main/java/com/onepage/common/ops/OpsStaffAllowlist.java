package com.onepage.common.ops;

import com.onepage.common.api.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营员工 userId 白名单。名单空则无人可进（含本机）。
 * auth 登录 {@code audience=ops} 与 moderation {@code /ops} 共用同一解析。
 */
public final class OpsStaffAllowlist {
  private OpsStaffAllowlist() {}

  public static Set<Long> parse(String raw) {
    if (!StringUtils.hasText(raw)) {
      return Set.of();
    }
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(Long::parseLong)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static void requireStaff(Set<Long> staffIds, long userId) {
    if (staffIds.isEmpty()) {
      throw BusinessException.forbidden("未配置运营员工名单");
    }
    if (!staffIds.contains(userId)) {
      throw BusinessException.forbidden("不是运营员工");
    }
  }
}
