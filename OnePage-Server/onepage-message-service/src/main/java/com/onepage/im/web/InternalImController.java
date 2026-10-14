package com.onepage.im.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.onepage.common.web.CurrentUser;
import com.onepage.im.dto.ImDtos;
import com.onepage.im.service.ImService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** content 分享笔记卡片。公网走 {@code POST /posts/{id}/share/im}，浏览器不能调这里。 */
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

  public record ShareCardRequest(String conversationId, String targetUserId, JsonNode content) {}
}
