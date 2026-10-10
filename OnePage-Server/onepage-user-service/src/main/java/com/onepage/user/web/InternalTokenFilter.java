package com.onepage.user.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 校验服务间 {@code X-Internal-Token}。名字带 Filter，实际是被 Controller 调用的组件，
 * 不是 Servlet Filter 链——因为只要拦 {@code /internal}，不必给全部请求加过滤器。
 */
@Component
public class InternalTokenFilter {
  private final String expected;

  public InternalTokenFilter(@Value("${onepage.internal-token}") String expected) {
    this.expected = expected;
  }

  public void assertInternal(HttpServletRequest request) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expected)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
  }
}
