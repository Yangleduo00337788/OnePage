package com.onepage.ai;

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
  public static final double TEMPERATURE_MAX = 2.0;
  public static final int MAX_TOKENS_CAP = 8192;

  public static final String MSG_NO_ANSWER = "暂时不能回答";
  public static final String MSG_NO_NOTES = "暂时不能根据站内笔记回答";
  public static final String MSG_NO_POLISH = "暂时不能润色";
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

  public static final String CHAT_SYSTEM =
      "你是一页社区助手耶耶。只根据提供的站内笔记回答生活方式问题。"
          + "不要编造链接或笔记。若材料不够，就直说暂时不能根据站内笔记回答。"
          + "用中文，口语，短段落。";

  public static final String PROBE_SYSTEM = "You are a health check. Reply with the single word ok.";
  public static final String PROBE_USER = "ping";
  public static final String MSG_PROBE_OK = "连通正常";
  public static final String MSG_PROBE_FAIL = "暂时连不上模型";
  public static final String MSG_PROBE_NO_KEY = "尚未配置密钥或地址";
}
