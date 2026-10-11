package com.onepage.common.api;

import org.springframework.http.HttpStatus;

/**
 * 可预期的业务失败。抛出后由 {@link GlobalExceptionHandler} 转成 JSON，不再走 500 堆栈页。
 * <p>
 * 继承 {@code RuntimeException}：调用方不用写 checked {@code throws}，事务遇到它会回滚。
 * HTTP 状态和 JSON {@code code} 拆开：浏览器看 status，前端逻辑看 code。
 */
public class BusinessException extends RuntimeException {
  private final String code;
  private final HttpStatus status;

  public BusinessException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  /** 工厂方法：少写 {@code new}，语义直接是「未登录」。 */
  public static BusinessException unauthorized(String message) {
    return new BusinessException(ErrorCodes.UNAUTHORIZED, message, HttpStatus.UNAUTHORIZED);
  }

  public static BusinessException forbidden(String message) {
    return new BusinessException(ErrorCodes.FORBIDDEN, message, HttpStatus.FORBIDDEN);
  }

  public static BusinessException notFound(String message) {
    return new BusinessException(ErrorCodes.NOT_FOUND, message, HttpStatus.NOT_FOUND);
  }

  public static BusinessException validation(String message) {
    return new BusinessException(ErrorCodes.VALIDATION_ERROR, message, HttpStatus.BAD_REQUEST);
  }

  public static BusinessException conflict(String message) {
    return new BusinessException(ErrorCodes.CONFLICT, message, HttpStatus.CONFLICT);
  }

  public static BusinessException rateLimited(String message) {
    return new BusinessException(ErrorCodes.RATE_LIMITED, message, HttpStatus.TOO_MANY_REQUESTS);
  }
}
