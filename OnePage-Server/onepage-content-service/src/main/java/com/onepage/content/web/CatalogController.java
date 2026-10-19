package com.onepage.content.web;

import com.onepage.content.dto.FeedDtos;
import com.onepage.content.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 社区/创作者读分类、活动、话题。不需登录。 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  public FeedDtos.PublicCatalog catalog() {
    return catalogService.publicCatalog();
  }

  @GetMapping("/topics")
  public List<FeedDtos.OpsTopic> topics(@RequestParam(value = "q", required = false) String q) {
    return catalogService.suggestTopics(q);
  }

  @GetMapping("/faq")
  public List<FeedDtos.OpsFaq> faq() {
    return catalogService.publicFaqs();
  }
}
