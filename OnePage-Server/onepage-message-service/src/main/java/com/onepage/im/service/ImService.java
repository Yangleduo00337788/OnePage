package com.onepage.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.im.client.ContentClient;
import com.onepage.im.client.UserClient;
import com.onepage.im.dto.ConversationHit;
import com.onepage.im.dto.ImDtos;
import com.onepage.im.dto.MessageHit;
import com.onepage.im.mapper.ConversationMapper;
import com.onepage.im.mapper.MemberMapper;
import com.onepage.im.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 单聊。pair_key 保证同一对用户只有一条会话。
 * 文本写入 content JSON 的 text 字段；图片只收已 complete 的 mediaId；笔记卡片走内部 sharePostCard。
 * 落库后经 Redis 推到在线 WebSocket。
 */
@Service
public class ImService {
  private static final Logger log = LoggerFactory.getLogger(ImService.class);
  private static final int TEXT_MAX = 2000;
  private static final Set<String> IMAGE_MIMES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  private final ConversationMapper conversationMapper;
  private final MemberMapper memberMapper;
  private final MessageMapper messageMapper;
  private final UserClient userClient;
  private final ContentClient contentClient;
  private final ObjectMapper objectMapper;
  private final ImPushHub pushHub;
  private final String internalToken;
  private final String mediaPublicBase;

  public ImService(
      ConversationMapper conversationMapper,
      MemberMapper memberMapper,
      MessageMapper messageMapper,
      UserClient userClient,
      ContentClient contentClient,
      ObjectMapper objectMapper,
      ImPushHub pushHub,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.media-public-base}") String mediaPublicBase) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.messageMapper = messageMapper;
    this.userClient = userClient;
    this.contentClient = contentClient;
    this.objectMapper = objectMapper;
    this.pushHub = pushHub;
    this.internalToken = internalToken;
    this.mediaPublicBase = mediaPublicBase;
  }

  public record OpenResult(String conversationId, boolean created) {}

  /** 不用事务：并发下 pair_key 冲突会触发 DataIntegrityViolation，套事务会把连接标成只能回滚。 */
  public OpenResult open(long userId, String peerUserIdRaw) {
    long peerId = parseId(peerUserIdRaw, "peerUserId");
    if (peerId == userId) {
      throw BusinessException.validation("不能给自己发私信");
    }
    if (brief(peerId) == null) {
      throw BusinessException.notFound("用户不存在");
    }
    String pair = pairKey(userId, peerId);
    Long existing = conversationMapper.findIdByPair(pair);
    if (existing != null) {
      return new OpenResult(String.valueOf(existing), false);
    }
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now();
    try {
      conversationMapper.insert(id, pair, now);
      memberMapper.insert(id, userId, now);
      memberMapper.insert(id, peerId, now);
      return new OpenResult(String.valueOf(id), true);
    } catch (DataIntegrityViolationException ex) {
      Long again = conversationMapper.findIdByPair(pair);
      if (again == null) {
        throw ex;
      }
      return new OpenResult(String.valueOf(again), false);
    }
  }

  public ImDtos.ConversationList list(long userId) {
    List<ConversationHit> hits = conversationMapper.listForUser(userId);
    if (hits == null || hits.isEmpty()) {
      return new ImDtos.ConversationList(List.of());
    }
    LinkedHashSet<Long> peerIds = new LinkedHashSet<>();
    for (ConversationHit hit : hits) {
      if (hit.getPeerId() != null) {
        peerIds.add(hit.getPeerId());
      }
    }
    Map<Long, UserSummary> peers = loadUsers(peerIds);
    List<ImDtos.ConversationItem> items = new ArrayList<>();
    for (ConversationHit hit : hits) {
      UserSummary peer = peers.get(hit.getPeerId());
      ImDtos.UserBrief brief = peer == null
          ? new ImDtos.UserBrief(String.valueOf(hit.getPeerId()), "", "用户", "")
          : new ImDtos.UserBrief(peer.userId(), peer.pageId(), peer.nickname(), peer.avatarUrl());
      ImDtos.LastMessage last = null;
      if (hit.getLastMessageId() != null) {
        last = new ImDtos.LastMessage(
            String.valueOf(hit.getLastMessageId()),
            hit.getLastMsgType() == null ? "text" : hit.getLastMsgType(),
            preview(hit.getLastContent()),
            iso(hit.getLastCreatedAt()));
      }
      items.add(new ImDtos.ConversationItem(
          String.valueOf(hit.getConversationId()),
          brief,
          last,
          hit.getUnreadCount() == null ? 0 : hit.getUnreadCount(),
          iso(hit.getUpdatedAt())));
    }
    return new ImDtos.ConversationList(items);
  }

  public ImDtos.MessageList messages(long userId, long conversationId, String beforeIdRaw, Integer limit) {
    requireMember(conversationId, userId);
    int size = limit == null ? 30 : Math.min(Math.max(limit, 1), 50);
    Long beforeId = null;
    if (StringUtils.hasText(beforeIdRaw)) {
      beforeId = parseId(beforeIdRaw, "beforeId");
    }
    List<MessageHit> rows = messageMapper.page(conversationId, beforeId, size);
    if (rows == null) {
      rows = List.of();
    }
    Collections.reverse(rows);
    if (beforeId == null && !rows.isEmpty()) {
      Long lastId = rows.getLast().getId();
      if (lastId != null) {
        memberMapper.markRead(conversationId, userId, lastId);
      }
    }
    List<ImDtos.MessageItem> items = new ArrayList<>();
    for (MessageHit row : rows) {
      items.add(new ImDtos.MessageItem(
          String.valueOf(row.getId()),
          String.valueOf(row.getSenderId()),
          row.getMsgType(),
          parseJson(row.getContent()),
          iso(row.getCreatedAt())));
    }
    return new ImDtos.MessageList(items);
  }

  @Transactional
  public ImDtos.MessageItem send(long userId, long conversationId, ImDtos.SendRequest body) {
    requireMember(conversationId, userId);
    if (body == null || !StringUtils.hasText(body.msgType()) || body.content() == null) {
      throw BusinessException.validation("消息不完整");
    }
    if ("text".equals(body.msgType())) {
      return persistText(userId, conversationId, body.content());
    }
    if ("image".equals(body.msgType())) {
      return persistImage(userId, conversationId, body.content());
    }
    throw BusinessException.validation("不支持的消息类型");
  }

  private ImDtos.MessageItem persistText(long userId, long conversationId, JsonNode content) {
    JsonNode textNode = content.get("text");
    if (textNode == null || !textNode.isTextual() || !StringUtils.hasText(textNode.asText())) {
      throw BusinessException.validation("文本不能为空");
    }
    String text = textNode.asText().trim();
    if (text.length() > TEXT_MAX) {
      throw BusinessException.validation("文本过长");
    }
    return persist(userId, conversationId, "text", writeJson(Map.of("text", text)));
  }

  private ImDtos.MessageItem persistImage(long userId, long conversationId, JsonNode content) {
    String mediaIdRaw = textOf(content, "mediaId");
    long mediaId = parseId(mediaIdRaw, "mediaId");
    ContentClient.MediaInfo media;
    try {
      media = contentClient.getMedia(internalToken, mediaId);
    } catch (RuntimeException ex) {
      log.warn("im image media {} lookup failed", mediaId, ex);
      throw BusinessException.notFound("图片不存在");
    }
    if (media == null
        || media.ownerId() == null
        || !media.ownerId().equals(userId)
        || media.status() == null
        || media.status() != 1
        || !StringUtils.hasText(media.objectKey())
        || !media.objectKey().startsWith("im_image/")
        || media.mimeType() == null
        || !IMAGE_MIMES.contains(media.mimeType())) {
      throw BusinessException.validation("图片不可用");
    }
    String url = publicUrl(media.objectKey());
    return persist(userId, conversationId, "image", writeJson(Map.of(
        "mediaId", String.valueOf(mediaId),
        "url", url)));
  }

  private String writeJson(Map<String, String> body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception ex) {
      throw BusinessException.validation("消息序列化失败");
    }
  }

  private String publicUrl(String objectKey) {
    String base = mediaPublicBase.endsWith("/")
        ? mediaPublicBase.substring(0, mediaPublicBase.length() - 1)
        : mediaPublicBase;
    return base + "/" + objectKey;
  }

  public ImDtos.MessageItem sharePostCard(
      long senderId, String conversationIdRaw, String targetUserIdRaw, JsonNode content) {
    if (content == null || !StringUtils.hasText(textOf(content, "postId"))) {
      throw BusinessException.validation("笔记卡片不完整");
    }
    long conversationId;
    if (StringUtils.hasText(conversationIdRaw)) {
      conversationId = parseId(conversationIdRaw, "conversationId");
    } else if (StringUtils.hasText(targetUserIdRaw)) {
      conversationId = Long.parseLong(open(senderId, targetUserIdRaw).conversationId());
    } else {
      throw BusinessException.validation("请选择好友");
    }
    requireMember(conversationId, senderId);
    String payload;
    try {
      payload = objectMapper.writeValueAsString(Map.of(
          "postId", textOf(content, "postId"),
          "title", textOf(content, "title"),
          "coverUrl", textOf(content, "coverUrl"),
          "authorNickname", textOf(content, "authorNickname")));
    } catch (Exception ex) {
      throw BusinessException.validation("消息序列化失败");
    }
    return persist(senderId, conversationId, "post_card", payload);
  }

  private ImDtos.MessageItem persist(long senderId, long conversationId, String msgType, String payload) {
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now();
    messageMapper.insert(id, conversationId, senderId, msgType, payload, now);
    conversationMapper.touch(conversationId, now);
    memberMapper.markRead(conversationId, senderId, id);
    ImDtos.MessageItem item = new ImDtos.MessageItem(
        String.valueOf(id),
        String.valueOf(senderId),
        msgType,
        parseJson(payload),
        iso(now));
    Long peerId = memberMapper.findPeerId(conversationId, senderId);
    String conversationKey = String.valueOf(conversationId);
    Runnable push = () -> {
      if (peerId != null) {
        pushHub.publish(peerId, conversationKey, item);
      }
      pushHub.publish(senderId, conversationKey, item);
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          push.run();
        }
      });
    } else {
      push.run();
    }
    return item;
  }

  public ImDtos.UnreadCount unread(long userId) {
    return new ImDtos.UnreadCount(messageMapper.countUnread(userId));
  }

  private void requireMember(long conversationId, long userId) {
    if (conversationMapper.requireMember(conversationId, userId) == null) {
      throw BusinessException.notFound("会话不存在");
    }
  }

  private UserSummary brief(long userId) {
    Map<Long, UserSummary> map = loadUsers(List.of(userId));
    return map.get(userId);
  }

  private Map<Long, UserSummary> loadUsers(java.util.Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    try {
      List<UserSummary> briefs = userClient.briefs(internalToken, new ArrayList<>(ids));
      if (briefs == null) {
        return Map.of();
      }
      return briefs.stream()
          .filter(b -> b != null && b.userId() != null)
          .collect(Collectors.toMap(b -> Long.parseLong(b.userId()), Function.identity(), (a, b) -> a));
    } catch (RuntimeException ex) {
      log.warn("load im users failed", ex);
      return Map.of();
    }
  }

  private static String pairKey(long a, long b) {
    return a < b ? a + "_" + b : b + "_" + a;
  }

  private static long parseId(String raw, String field) {
    if (!StringUtils.hasText(raw)) {
      throw BusinessException.validation(field + " 不合法");
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      throw BusinessException.validation(field + " 不合法");
    }
  }

  private JsonNode parseJson(String raw) {
    if (!StringUtils.hasText(raw)) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(raw);
    } catch (Exception ex) {
      return objectMapper.createObjectNode().put("text", raw);
    }
  }

  private String preview(String raw) {
    JsonNode node = parseJson(raw);
    JsonNode text = node.get("text");
    if (text != null && text.isTextual() && StringUtils.hasText(text.asText())) {
      return text.asText();
    }
    JsonNode title = node.get("title");
    if (title != null && title.isTextual() && StringUtils.hasText(title.asText())) {
      return "[笔记] " + title.asText();
    }
    JsonNode url = node.get("url");
    if (url != null && url.isTextual() && StringUtils.hasText(url.asText())) {
      return "[图片]";
    }
    return "";
  }

  private static String textOf(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || !value.isTextual() ? "" : value.asText().trim();
  }

  private static String iso(LocalDateTime time) {
    return time == null ? "" : time.toString();
  }
}
