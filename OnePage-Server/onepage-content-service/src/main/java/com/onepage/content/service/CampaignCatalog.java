package com.onepage.content.service;

import java.util.Map;

/** 发布可选活动。标题写入笔记快照，活动中心服务未建前用这一份。 */
final class CampaignCatalog {
  static final Map<String, String> TITLES = Map.of(
      "e1", "春季创作激励计划",
      "e2", "周末话题挑战 · 一页日常");

  private CampaignCatalog() {}
}
