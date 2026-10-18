package com.onepage.content.web;

import com.onepage.common.api.BusinessException;
import com.onepage.common.web.GatewayHeaders;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营目录 CRUD。改分类/活动/话题用 POST 不用 PATCH：
 * Feign 默认 JDK HttpURLConnection 不能发 PATCH，会 ProtocolException。
 */
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {
  private final CatalogService catalogService;
  private final String expectedToken;

  public InternalCatalogController(
      CatalogService catalogService,
      @Value("${onepage.internal-token}") String expectedToken) {
    this.catalogService = catalogService;
    this.expectedToken = expectedToken;
  }

  @GetMapping("/search/topics")
  public FeedDtos.TopicIndexPage searchableTopics(
      HttpServletRequest request,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    assertInternal(request);
    return catalogService.listSearchableTopics(cursor, limit);
  }

  @GetMapping("/catalog/ops")
  public FeedDtos.OpsCatalog catalog(HttpServletRequest request) {
    assertInternal(request);
    return catalogService.opsCatalog();
  }

  @GetMapping("/stats")
  public FeedDtos.ContentStats stats(HttpServletRequest request) {
    assertInternal(request);
    return catalogService.stats();
  }

  @PostMapping("/categories")
  public FeedDtos.OpsCategory createCategory(
      HttpServletRequest request, @RequestBody FeedDtos.CategorySpec spec) {
    assertInternal(request);
    return catalogService.saveCategory(null, spec);
  }

  @PostMapping("/categories/{id}")
  public FeedDtos.OpsCategory patchCategory(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestBody FeedDtos.CategorySpec spec) {
    assertInternal(request);
    return catalogService.saveCategory(id, spec);
  }

  @PostMapping("/categories/{id}/enabled")
  public ResponseEntity<Void> categoryEnabled(
      HttpServletRequest request,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value) {
    assertInternal(request);
    catalogService.setCategoryEnabled(id, value);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/campaigns")
  public FeedDtos.OpsCampaign createCampaign(
      HttpServletRequest request, @RequestBody FeedDtos.CampaignSpec spec) {
    assertInternal(request);
    return catalogService.saveCampaign(spec, true);
  }

  @PostMapping("/campaigns/{eventId}")
  public FeedDtos.OpsCampaign patchCampaign(
      HttpServletRequest request,
      @PathVariable("eventId") String eventId,
      @RequestBody FeedDtos.CampaignSpec spec) {
    assertInternal(request);
    FeedDtos.CampaignSpec body = new FeedDtos.CampaignSpec(
        eventId,
        spec.title(),
        spec.intro(),
        spec.coverUrl(),
        spec.status(),
        spec.sortOrder(),
        spec.enabled());
    return catalogService.saveCampaign(body, false);
  }

  @PostMapping("/campaigns/{eventId}/enabled")
  public ResponseEntity<Void> campaignEnabled(
      HttpServletRequest request,
      @PathVariable("eventId") String eventId,
      @RequestParam("value") boolean value) {
    assertInternal(request);
    catalogService.setCampaignEnabled(eventId, value);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/topics")
  public FeedDtos.OpsTopic createTopic(HttpServletRequest request, @RequestBody FeedDtos.TopicSpec spec) {
    assertInternal(request);
    return catalogService.saveTopic(null, spec);
  }

  @PostMapping("/topics/{id}")
  public FeedDtos.OpsTopic patchTopic(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestBody FeedDtos.TopicSpec spec) {
    assertInternal(request);
    return catalogService.saveTopic(id, spec);
  }

  @PostMapping("/topics/{id}/enabled")
  public ResponseEntity<Void> topicEnabled(
      HttpServletRequest request,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value) {
    assertInternal(request);
    catalogService.setTopicEnabled(id, value);
    return ResponseEntity.noContent().build();
  }

  private void assertInternal(HttpServletRequest request) {
    String token = request.getHeader(GatewayHeaders.INTERNAL_TOKEN);
    if (token == null || !token.equals(expectedToken)) {
      throw BusinessException.unauthorized("内部调用未授权");
    }
  }
}
