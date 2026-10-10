package com.onepage.common.web;

/**
 * 服务间约定的 HTTP 头名。改名字要同时改网关 Filter 和 Feign 客户端。
 * 不用魔法字符串散落各处，避免拼错一个字母就鉴权失效。
 */
public final class GatewayHeaders {
  /**
   * 网关在 Session 校验通过后写入。下游 {@link CurrentUser} 只读这个头，
   * 不解析 Cookie——业务服务没有 Redis Session 客户端，解析不了。
   */
  public static final String USER_ID = "X-User-Id";
  /**
   * 服务调服务时的共享密钥（yml {@code onepage.internal-token}）。
   * 内部建档接口不能只靠「没走网关」——开发机端口可能被直接访问。
   */
  public static final String INTERNAL_TOKEN = "X-Internal-Token";

  private GatewayHeaders() {}
}
