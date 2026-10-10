package com.onepage.common.api;

/**
 * 业务服务对外 JSON 信封。
 * <p>
 * {@code record}：不可变数据类，编译器自动生成构造器、getter、equals。字段就是 JSON 里的键。
 * 网关 401 自己拼 Map，不走这个类型；业务 Controller 的返回值会被 Spring 序列化成同样形状。
 * 前端 {@code api-client} 按 {@code code / message / data} 解析。
 */
public record ApiResponse<T>(
    /** 业务码：成功固定 {@code OK}，失败与 {@link ErrorCodes} 对齐。不是 HTTP 状态码。 */
    String code,
    String message,
    /** 本次请求追踪号，来自 {@link RequestIds}（网关也会设 X-Request-Id）。 */
    String requestId,
    /** 成功时的载荷；失败时为 null。 */
    T data
) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>("OK", "ok", RequestIds.current(), data);
  }

  public static ApiResponse<Void> ok() {
    return ok(null);
  }

  public static ApiResponse<Void> error(String code, String message) {
    return new ApiResponse<>(code, message, RequestIds.current(), null);
  }
}
