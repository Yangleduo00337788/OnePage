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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 润色走 JSON 改写；问答先检索公开笔记与话题再生成。失败不编造。 */
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
    AiSettings.Stored stored = aiSettings.current();
    String system = continueWrite ? stored.continueSystem() : stored.polishSystem();
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
    AiSettings.Stored stored = aiSettings.current();
    Retrieved found = retrieve(question, stored.retrieveLimit());
    List<AiDtos.Citation> citations = toCitations(found.posts());
    if (found.posts().isEmpty() && found.topics().isEmpty()) {
      return new AiDtos.ChatResult(AiConstants.MSG_NO_NOTES, List.of());
    }
    String answer = llmClient.complete(
        stored.chatSystem(), buildChatUser(question, found.posts(), found.topics(), stored.contextBodyMax()));
    if (!StringUtils.hasText(answer)) {
      return new AiDtos.ChatResult(AiConstants.MSG_NO_ANSWER, citations);
    }
    return new AiDtos.ChatResult(answer, citations);
  }

  public void writeChatStream(AiDtos.ChatSpec spec, OutputStream out) throws IOException {
    String question = spec == null || spec.question() == null ? "" : spec.question().trim();
    if (!StringUtils.hasText(question)) {
      emit(out, "error", new AiDtos.StreamError("请输入问题"));
      return;
    }
    if (!aiSettings.current().chatEnabled() || !llmClient.configured()) {
      emit(out, "stage", new AiDtos.StreamStage("notes", 0));
      emit(out, "delta", new AiDtos.StreamDelta(AiConstants.MSG_NO_ANSWER));
      emit(out, "done", new AiDtos.StreamDone(List.of()));
      return;
    }
    AiSettings.Stored stored = aiSettings.current();
    emit(out, "stage", new AiDtos.StreamStage("retrieve", 0));
    Retrieved found = retrieve(question, stored.retrieveLimit());
    List<AiDtos.Citation> citations = toCitations(found.posts());
    emit(out, "stage", new AiDtos.StreamStage("notes", found.posts().size()));
    if (found.posts().isEmpty() && found.topics().isEmpty()) {
      emit(out, "delta", new AiDtos.StreamDelta(AiConstants.MSG_NO_NOTES));
      emit(out, "done", new AiDtos.StreamDone(List.of()));
      return;
    }
    emit(out, "stage", new AiDtos.StreamStage("write", found.posts().size()));
    String user = buildChatUser(question, found.posts(), found.topics(), stored.contextBodyMax());
    try {
      if (Boolean.TRUE.equals(stored.streamEnabled())) {
        llmClient.completeStream(
            stored.chatSystem(),
            user,
            piece -> {
              try {
                emit(out, "delta", new AiDtos.StreamDelta(piece));
              } catch (IOException ex) {
                throw new IllegalStateException(ex);
              }
            });
      } else {
        String answer = llmClient.complete(stored.chatSystem(), user);
        if (!StringUtils.hasText(answer)) {
          answer = AiConstants.MSG_NO_ANSWER;
        }
        emit(out, "delta", new AiDtos.StreamDelta(answer));
      }
      emit(out, "done", new AiDtos.StreamDone(citations));
    } catch (BusinessException ex) {
      emit(out, "error", new AiDtos.StreamError(ex.getMessage()));
    } catch (IllegalStateException ex) {
      log.warn("ai stream write failed", ex);
    } catch (Exception ex) {
      log.warn("ai stream failed", ex);
      emit(out, "error", new AiDtos.StreamError(AiConstants.MSG_NO_ANSWER));
    }
  }

  private void emit(OutputStream out, String event, Object payload) throws IOException {
    String json = objectMapper.writeValueAsString(payload);
    out.write(("event: " + event + "\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private static boolean isContinue(String mode) {
    if (mode == null) {
      return false;
    }
    String value = mode.trim().toLowerCase();
    return AiConstants.MODE_CONTINUE.equals(value) || "续写".equals(mode.trim());
  }

  private Retrieved retrieve(String question, int limit) {
    try {
      SearchClient.SearchHit page = searchClient.search(question, "all", limit);
      List<SearchClient.PostHit> posts = List.of();
      List<SearchClient.TopicHit> topics = List.of();
      if (page != null && page.posts() != null) {
        posts = page.posts().stream()
            .filter(item -> item != null && StringUtils.hasText(item.postId()))
            .toList();
      }
      if (page != null && page.topics() != null) {
        topics = page.topics().stream()
            .filter(item -> item != null && StringUtils.hasText(item.name()))
            .toList();
      }
      return new Retrieved(posts, topics);
    } catch (RuntimeException ex) {
      log.warn("ai retrieve failed", ex);
      return new Retrieved(List.of(), List.of());
    }
  }

  private record Retrieved(List<SearchClient.PostHit> posts, List<SearchClient.TopicHit> topics) {}

  private static List<AiDtos.Citation> toCitations(List<SearchClient.PostHit> posts) {
    List<AiDtos.Citation> out = new ArrayList<>();
    for (SearchClient.PostHit post : posts) {
      String snippet = firstLine(post.title(), post.body());
      String nick = post.author() == null ? "" : nullToEmpty(post.author().nickname());
      String pageId = post.author() == null ? "" : nullToEmpty(post.author().pageId());
      out.add(
          new AiDtos.Citation(
              post.postId(),
              snippet,
              nullToEmpty(post.title()),
              nullToEmpty(post.coverUrl()),
              nick,
              pageId));
    }
    return List.copyOf(out);
  }

  private static String buildChatUser(
      String question,
      List<SearchClient.PostHit> posts,
      List<SearchClient.TopicHit> topics,
      int bodyMax) {
    StringBuilder sb = new StringBuilder();
    sb.append("问题：").append(question);
    sb.append("\n\n回答要求：归纳成短段落，不要复述全文；引用用 [1][2] 对应下面编号。\n\n站内笔记：\n");
    int i = 1;
    for (SearchClient.PostHit post : posts) {
      String nick = post.author() == null ? "" : nullToEmpty(post.author().nickname());
      String body = nullToEmpty(post.body());
      if (body.length() > bodyMax) {
        body = body.substring(0, bodyMax);
      }
      sb.append('[')
          .append(i++)
          .append("] 标题=")
          .append(nullToEmpty(post.title()))
          .append(" 作者=")
          .append(nick)
          .append('\n')
          .append(body)
          .append("\n\n");
    }
    if (topics != null && !topics.isEmpty()) {
      sb.append("站内话题：\n");
      for (SearchClient.TopicHit topic : topics) {
        sb.append("- ").append(nullToEmpty(topic.name())).append('\n');
      }
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
