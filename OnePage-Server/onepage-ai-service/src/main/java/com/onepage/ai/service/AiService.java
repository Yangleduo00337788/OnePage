package com.onepage.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.ai.AiConstants;
import com.onepage.ai.client.SearchClient;
import com.onepage.ai.dto.AiDtos;
import com.onepage.common.api.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 润色走 JSON 改写；问答先检索公开笔记再生成。失败不编造。 */
@Service
public class AiService {
  private static final Logger log = LoggerFactory.getLogger(AiService.class);

  private final LlmClient llmClient;
  private final SearchClient searchClient;
  private final ObjectMapper objectMapper;
  private final AiSettings aiSettings;

  public AiService(
      LlmClient llmClient,
      SearchClient searchClient,
      ObjectMapper objectMapper,
      AiSettings aiSettings) {
    this.llmClient = llmClient;
    this.searchClient = searchClient;
    this.objectMapper = objectMapper;
    this.aiSettings = aiSettings;
  }

  public AiDtos.PolishResult polish(AiDtos.PolishSpec spec) {
    String title = spec == null || spec.title() == null ? "" : spec.title().trim();
    String body = spec == null || spec.body() == null ? "" : spec.body().trim();
    if (!StringUtils.hasText(title) && !StringUtils.hasText(body)) {
      throw BusinessException.validation("请先写标题或正文");
    }
    if (!aiSettings.current().polishEnabled() || !llmClient.configured()) {
      throw BusinessException.configMissing(AiConstants.MSG_NO_POLISH);
    }
    boolean continueWrite = isContinue(spec == null ? null : spec.mode());
    String system = continueWrite ? AiConstants.CONTINUE_SYSTEM : AiConstants.POLISH_SYSTEM;
    String user = "title: " + title + "\nbody:\n" + body;
    String raw = llmClient.complete(system, user);
    JsonNode node = readObject(raw);
    String nextTitle = textOf(node, "title");
    String nextBody = textOf(node, "body");
    if (!StringUtils.hasText(nextTitle) && !StringUtils.hasText(nextBody)) {
      throw BusinessException.configMissing(AiConstants.MSG_NO_POLISH);
    }
    if (continueWrite || !StringUtils.hasText(nextTitle)) {
      nextTitle = title;
    }
    if (!StringUtils.hasText(nextBody)) {
      nextBody = body;
    }
    if (nextTitle.length() > AiConstants.TITLE_MAX) {
      nextTitle = nextTitle.substring(0, AiConstants.TITLE_MAX);
    }
    return new AiDtos.PolishResult(nextTitle, nextBody);
  }

  public AiDtos.ChatResult chat(AiDtos.ChatSpec spec) {
    String question = spec == null || spec.question() == null ? "" : spec.question().trim();
    if (!StringUtils.hasText(question)) {
      throw BusinessException.validation("请输入问题");
    }
    if (!aiSettings.current().chatEnabled() || !llmClient.configured()) {
      return new AiDtos.ChatResult(AiConstants.MSG_NO_ANSWER, List.of());
    }
    List<SearchClient.PostHit> posts = retrieve(question);
    List<AiDtos.Citation> citations = toCitations(posts);
    if (posts.isEmpty()) {
      return new AiDtos.ChatResult(AiConstants.MSG_NO_NOTES, List.of());
    }
    String answer = llmClient.complete(AiConstants.CHAT_SYSTEM, buildChatUser(question, posts));
    if (!StringUtils.hasText(answer)) {
      return new AiDtos.ChatResult(AiConstants.MSG_NO_ANSWER, citations);
    }
    return new AiDtos.ChatResult(answer, citations);
  }

  private static boolean isContinue(String mode) {
    if (mode == null) {
      return false;
    }
    String value = mode.trim().toLowerCase();
    return AiConstants.MODE_CONTINUE.equals(value) || "续写".equals(mode.trim());
  }

  private List<SearchClient.PostHit> retrieve(String question) {
    try {
      SearchClient.SearchHit page =
          searchClient.search(question, "post", AiConstants.RETRIEVE_LIMIT);
      if (page == null || page.posts() == null) {
        return List.of();
      }
      return page.posts().stream().filter(item -> item != null && StringUtils.hasText(item.postId())).toList();
    } catch (Exception ex) {
      log.warn("ai retrieve failed", ex);
      return List.of();
    }
  }

  private static List<AiDtos.Citation> toCitations(List<SearchClient.PostHit> posts) {
    List<AiDtos.Citation> out = new ArrayList<>();
    for (SearchClient.PostHit post : posts) {
      String snippet = firstLine(post.title(), post.body());
      out.add(new AiDtos.Citation(post.postId(), snippet));
    }
    return List.copyOf(out);
  }

  private static String buildChatUser(String question, List<SearchClient.PostHit> posts) {
    StringBuilder sb = new StringBuilder();
    sb.append("问题：").append(question).append("\n\n站内笔记：\n");
    int i = 1;
    for (SearchClient.PostHit post : posts) {
      String nick = post.author() == null ? "" : nullToEmpty(post.author().nickname());
      String body = nullToEmpty(post.body());
      if (body.length() > AiConstants.CONTEXT_BODY_MAX) {
        body = body.substring(0, AiConstants.CONTEXT_BODY_MAX);
      }
      sb.append(i++)
          .append(". postId=")
          .append(post.postId())
          .append(" 作者=")
          .append(nick)
          .append(" 标题=")
          .append(nullToEmpty(post.title()))
          .append('\n')
          .append(body)
          .append("\n\n");
    }
    return sb.toString();
  }

  private JsonNode readObject(String raw) {
    String text = raw == null ? "" : raw.trim();
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start >= 0 && end > start) {
      text = text.substring(start, end + 1);
    }
    try {
      JsonNode node = objectMapper.readTree(text);
      return node == null || node.isMissingNode() ? objectMapper.createObjectNode() : node;
    } catch (Exception ex) {
      log.warn("polish json parse failed");
      throw BusinessException.configMissing(AiConstants.MSG_NO_POLISH);
    }
  }

  private static String textOf(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText().trim() : "";
  }

  private static String firstLine(String title, String body) {
    String head = StringUtils.hasText(title) ? title.trim() : "";
    String rest = nullToEmpty(body).replace('\n', ' ').trim();
    String joined = StringUtils.hasText(head) ? head + " " + rest : rest;
    if (joined.length() > AiConstants.SNIPPET_MAX) {
      return joined.substring(0, AiConstants.SNIPPET_MAX);
    }
    return joined;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
