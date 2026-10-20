package com.onepage.common.text;

import com.onepage.common.api.BusinessException;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/** 运营违禁词：忽略大小写、去掉空白后的子串包含。图/视频画面不扫。 */
public final class BannedText {
  private BannedText() {}

  public static final List<String> DEFAULT_WORDS = List.of(
      "代孕", "办证", "发票", "枪支", "毒品", "约炮", "色情", "赌博",
      "加微信", "加vx", "加v信", "澳门赌场", "六合彩");

  public static void assertClean(String text, List<String> words) {
    if (!StringUtils.hasText(text) || words == null || words.isEmpty()) {
      return;
    }
    String compact = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    if (compact.isEmpty()) {
      return;
    }
    for (String word : words) {
      if (!StringUtils.hasText(word)) {
        continue;
      }
      String needle = word.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
      if (!needle.isEmpty() && compact.contains(needle)) {
        throw BusinessException.validation("含有不允许的内容");
      }
    }
  }
}
