package com.onepage.common.api;

/**
 * 业务错误码字符串。前端用 {@code ApiError.code} 分支，不要只看 HTTP 数字。
 * {@code final class + 私有构造}：工具类不能被 new、不能被继承。
 */
public final class ErrorCodes {
  private ErrorCodes() {}

  public static final String UNAUTHORIZED = "UNAUTHORIZED";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String NOT_FOUND = "NOT_FOUND";
  /** 参数不合法（对应 HTTP 400）。 */
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  /** 发短信过频等（对应 HTTP 429）。 */
  public static final String RATE_LIMITED = "RATE_LIMITED";
  /** 手机号/邮箱已占用等（对应 HTTP 409）。 */
  public static final String CONFLICT = "CONFLICT";
  /** OAuth client-id 没配等（对应 HTTP 503）。 */
  public static final String CONFIG_MISSING = "CONFIG_MISSING";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
