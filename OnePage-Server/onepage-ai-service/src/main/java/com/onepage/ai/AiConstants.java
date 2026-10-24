package com.onepage.ai;

import java.time.Duration;
import java.util.List;

/** 润色与问答用到的限额、文案。不要在业务里散落魔法值。 */
public final class AiConstants {
  private AiConstants() {}

  public static final int RETRIEVE_LIMIT = 8;
  public static final int RETRIEVE_MAX = 20;
  public static final int TITLE_MAX = 20;
  public static final int SNIPPET_MAX = 280;
  public static final int CONTEXT_BODY_MAX = 400;
  public static final int CONTEXT_BODY_CAP = 2000;
  public static final int QUESTIONS_MAX = 6;
  public static final int PROMPT_MAX = 4000;
  public static final double TEMPERATURE_DEFAULT = 0.4;
  public static final double REVIEW_TEMPERATURE = 0.1;
  public static final int REVIEW_MAX_TOKENS = 256;
  public static final double TEMPERATURE_MAX = 2.0;
  public static final int MAX_TOKENS_CAP = 8192;

  public static final int CHAT_IMAGE_MAX = 4;
  public static final int CHAT_IMAGE_BYTES_MAX = 10 * 1024 * 1024;
  public static final Duration IMAGE_FETCH_TIMEOUT = Duration.ofSeconds(10L);

  public static final String MSG_NO_ANSWER = "暂时不能回答";
  public static final String MSG_NO_NOTES = "暂时不能根据站内笔记回答";
  public static final String MSG_NO_VISION = "暂时看不了图";
  public static final String MSG_NO_VISION_FETCH = "暂时看不了这张图";
  public static final String MSG_NO_POLISH = "暂时不能润色";
  public static final String CHAT_VISION_ADDON =
      "用户附了图片。必须直接看这些图回答，不要说看不见图或只拿到了链接。"
          + "没有站内笔记也可以只根据图片回答，不要说暂时不能根据站内笔记回答。";
  public static final String MODE_OPTIMIZE = "optimize";
  public static final String MODE_CONTINUE = "continue";
  public static final String REDIS_SETTINGS_KEY = "onepage:ai:settings";
  public static final String DEFAULT_YEH_PRESET = "帮我发现今天值得一看的生活方式笔记";
  public static final List<String> DEFAULT_YEH_QUESTIONS = List.of(
      "周末去哪玩比较合适？",
      "帮我搭配一套通勤穿搭",
      "最近有哪些值得看的生活方式笔记？");

  public static final String POLISH_SYSTEM =
      "你是一页笔记编辑。把用户给的标题和正文优化得更清晰、口语、适合生活社区。"
          + "只输出一个 JSON 对象，键为 title 与 body，不要 Markdown、不要解释。"
          + "title 不超过 20 个汉字，不要加书名号。不要编造事实。";

  public static final String CONTINUE_SYSTEM =
      "你是一页笔记编辑。在用户已有正文后面续写，语气一致，不要推翻原文。"
          + "只输出一个 JSON 对象，键为 title 与 body。title 必须原样返回。"
          + "body 是「原文 + 续写」的完整正文。不要 Markdown、不要解释。不要编造事实。";

  public static final int REVIEW_IMAGE_MAX = 8;
  public static final int REVIEW_BODY_MAX = 4000;
  public static final int REVIEW_REASON_MAX = 200;
  public static final int REVIEW_RISK_LIMIT = 70;
  public static final int REVIEW_REJECT_LIMIT = 80;
  public static final int REVIEW_SCORE_MAX = 100;
  public static final String VERDICT_PASS = "pass";
  public static final String VERDICT_FLAG = "flag";
  public static final String VERDICT_REJECT = "reject";
  public static final String VERDICT_SKIP = "skip";
  public static final String LABEL_NORMAL = "normal";
  public static final String LABEL_AD = "ad";
  public static final String LABEL_PORN = "porn";
  public static final String LABEL_VIOLENCE = "violence";
  public static final List<String> REVIEW_LABELS =
      List.of(LABEL_NORMAL, LABEL_AD, LABEL_PORN, LABEL_VIOLENCE);

  public static final String CHAT_SYSTEM =
      "你是一页社区助手耶耶。只根据提供的站内笔记回答生活方式问题。"
          + "用中文口语，写 3 到 6 个短段落，可以分点。"
          + "禁止把材料正文整段复制进回答，只做归纳和建议。"
          + "每条具体建议末尾用方括号标材料编号，例如 [1]，编号必须对上材料。"
          + "不要编造链接、笔记或编号。材料不够就直说暂时不能根据站内笔记回答。"
          + "不要输出 JSON，不要 Markdown 标题。";

  public static final String REVIEW_SYSTEM =
      "你是一页社区笔记预审员。生活社区默认放行：没有明确敏感内容就必须过。"
          + "labels 只能从 normal、ad、porn、violence 里选，可多选。"
          + "日常分享、风景、吃穿玩、看不清或空泛描述：只用 normal，score 0–15。"
          + "明确微信/二维码/发票/导流成交才用 ad，分 50–70。"
          + "色情用 porn，暴力违法诈骗用 violence，分 80–100。"
          + "吃不准不要标 ad，用 normal 分 0–15。不要因为提到图片或视频就加分。"
          + "只输出一个 JSON 对象，键为 score（整数）、labels（字符串数组）、reason（短中文）。"
          + "不要 Markdown、不要解释。";

  public static final String REVIEW_VISION_SYSTEM =
      "你是一页社区笔记预审员。生活社区默认放行：画面没有明确敏感内容就必须过。"
          + "labels 只能从 normal、ad、porn、violence 里选，可多选。"
          + "生活照、风景、人物背影、纯色、占位、看不清：只用 normal，score 0–15。"
          + "画面里明确出现微信/二维码/发票/广告导流才用 ad，分 50–70。"
          + "色情用 porn，暴力违禁用 violence，分 80–100。"
          + "吃不准不要标 ad，不要因为不清晰就抬分。"
          + "不要根据标题编造画面里没有的内容。"
          + "只输出一个 JSON 对象，键为 score（整数）、labels（字符串数组）、reason（短中文）。"
          + "不要 Markdown、不要解释。";

  public static final String PROBE_SYSTEM = "You are a health check. Reply with the single word ok.";
  public static final String PROBE_USER = "ping";
  public static final String MSG_PROBE_OK = "连通正常";
  public static final String MSG_PROBE_FAIL = "暂时连不上模型";
  public static final String MSG_PROBE_NO_KEY = "尚未配置密钥或地址";
}
