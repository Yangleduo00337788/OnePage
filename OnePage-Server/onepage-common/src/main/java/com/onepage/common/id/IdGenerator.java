package com.onepage.common.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内简易发号。不用数据库自增：auth/user/content 各有自己的库，主键要能跨库当同一 userId。
 * <p>
 * 高位是相对纪元的毫秒，低 12 位是序号（{@code 0xFFF} = 4095）。
 * 不是完整雪花算法（没有机器号），多实例同一毫秒可能撞号——P0 单机可接受。
 * {@link AtomicLong}：多线程自增不会丢更新，比 {@code ++} 安全。
 */
public final class IdGenerator {
  /** 2024-01-01 起算，让 id 数字短一些。 */
  private static final long EPOCH = 1704067200000L;
  private static final AtomicLong SEQ = new AtomicLong();

  private IdGenerator() {}

  public static long nextId() {
    long ts = System.currentTimeMillis() - EPOCH;
    long seq = SEQ.incrementAndGet() & 0xFFF;
    return (ts << 12) | seq;
  }

  /**
   * 把 userId 编成对外 {@code pageId}（36 进制大写，类似短码主页路径 {@code /u/XXXX}）。
   * 不是加密，只是避免 URL 里直接暴露长数字。
   */
  public static String pageId(long userId) {
    String raw = Long.toString(userId, 36).toUpperCase();
    if (raw.length() < 8) {
      return ("00000000" + raw).substring(raw.length());
    }
    return raw.length() > 12 ? raw.substring(raw.length() - 12) : raw;
  }
}
