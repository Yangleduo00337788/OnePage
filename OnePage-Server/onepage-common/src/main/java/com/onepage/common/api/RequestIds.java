package com.onepage.common.api;

import java.util.UUID;

/**
 * 当前线程的 requestId。Tomcat 一个请求占用一个线程，用 {@link ThreadLocal} 存，互不串号。
 * 请求结束后必须 {@link #clear()}，否则线程池复用会带上一次的 id。
 * <p>
 * {@code ThreadLocal}：每个线程一份独立变量，不是全局单例值。
 */
public final class RequestIds {
  private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

  private RequestIds() {}

  public static String current() {
    String id = HOLDER.get();
    if (id == null) {
      id = UUID.randomUUID().toString().replace("-", "");
      HOLDER.set(id);
    }
    return id;
  }

  public static void clear() {
    HOLDER.remove();
  } // 这个clear方法没有调用啊。
}
