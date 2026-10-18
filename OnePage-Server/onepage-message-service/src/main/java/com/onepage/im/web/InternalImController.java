package com.onepage.im.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.onepage.common.web.CurrentUser;
import com.onepage.im.dto.ImDtos;
import com.onepage.im.service.ImService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内网：分享卡片 + 运营巡查。浏览器不能调这里。 */
@RestController
@RequestMapping("/internal/im")
public class InternalImController {
  private final ImService imService;
  private final InternalTokenFilter tokenFilter;

  public InternalImController(ImService imService, InternalTokenFilter tokenFilter) {
    this.imService = imService;
    this.tokenFilter = tokenFilter;
  }

  @PostMapping("/share-card")
  public ImDtos.MessageItem shareCard(HttpServletRequest request, @RequestBody ShareCardRequest body) {
    tokenFilter.assertInternal(request);
    return imService.sharePostCard(
        CurrentUser.requireUserId(request),
        body == null ? null : body.conversationId(),
        body == null ? null : body.targetUserId(),
        body == null ? null : body.content());
  }

  @PostMapping("/share-live-card")
  public ImDtos.MessageItem shareLiveCard(HttpServletRequest request, @RequestBody ShareCardRequest body) {
    tokenFilter.assertInternal(request);
    return imService.shareLiveCard(
        CurrentUser.requireUserId(request),
        body == null ? null : body.conversationId(),
        body == null ? null : body.targetUserId(),
        body == null ? null : body.content());
  }

  public record ShareCardRequest(String conversationId, String targetUserId, JsonNode content) {}

  @GetMapping("/ops/conversations")
  public ImDtos.OpsConversationPage opsConversations(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    tokenFilter.assertInternal(request);
    return imService.opsConversations(q, cursor, limit);
  }

  @GetMapping("/ops/conversations/{conversationId}/messages")
  public ImDtos.OpsMessagePage opsMessages(
      HttpServletRequest request,
      @PathVariable("conversationId") long conversationId,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    tokenFilter.assertInternal(request);
    return imService.opsMessages(conversationId, hidden, cursor, limit);
  }

  @PostMapping("/ops/messages/{messageId}/hide")
  public ResponseEntity<Void> hide(HttpServletRequest request, @PathVariable("messageId") long messageId) {
    tokenFilter.assertInternal(request);
    imService.opsHide(messageId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/ops/messages/{messageId}/restore")
  public ResponseEntity<Void> restore(HttpServletRequest request, @PathVariable("messageId") long messageId) {
    tokenFilter.assertInternal(request);
    imService.opsRestore(messageId);
    return ResponseEntity.noContent().build();
  }
}
