package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Campaign;
import com.onepage.content.entity.Comment;
import com.onepage.content.entity.Post;
import com.onepage.content.entity.PostCategory;
import com.onepage.content.entity.Topic;
import com.onepage.content.mapper.CampaignMapper;
import com.onepage.content.mapper.CommentMapper;
import com.onepage.content.mapper.PostCategoryMapper;
import com.onepage.content.mapper.PostMapper;
import com.onepage.content.mapper.TopicMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 分类 / 活动 / 话题。表空时分类和活动回落到代码常量，避免未跑 005 时发帖全挂。 */
@Service
public class CatalogService {
  private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
  private static final Pattern HASH = Pattern.compile("#([^\\s#]{1,32})");
  private static final Set<String> FALLBACK_CATEGORIES =
      Set.of("旅行", "美食", "时尚", "摄影", "生活", "运动", "游戏", "音乐");
  private static final Set<String> CAMPAIGN_STATUS = Set.of("live", "upcoming", "ended");

  private final PostCategoryMapper categoryMapper;
  private final CampaignMapper campaignMapper;
  private final TopicMapper topicMapper;
  private final PostMapper postMapper;
  private final CommentMapper commentMapper;
  private final SearchIndexer searchIndexer;

  public CatalogService(
      PostCategoryMapper categoryMapper,
      CampaignMapper campaignMapper,
      TopicMapper topicMapper,
      PostMapper postMapper,
      CommentMapper commentMapper,
      @Lazy SearchIndexer searchIndexer) {
    this.categoryMapper = categoryMapper;
    this.campaignMapper = campaignMapper;
    this.topicMapper = topicMapper;
    this.postMapper = postMapper;
    this.commentMapper = commentMapper;
    this.searchIndexer = searchIndexer;
  }

  public boolean isEnabledCategory(String name) {
    if (!StringUtils.hasText(name)) {
      return false;
    }
    List<PostCategory> rows = listCategories();
    if (rows.isEmpty()) {
      return FALLBACK_CATEGORIES.contains(name.trim());
    }
    for (PostCategory row : rows) {
      if (name.trim().equals(row.getName()) && enabled(row.getEnabled())) {
        return true;
      }
    }
    return false;
  }

  public String campaignTitle(String eventId) {
    if (!StringUtils.hasText(eventId)) {
      return "";
    }
    try {
      Campaign row = campaignMapper.selectById(eventId.trim());
      if (row != null && enabled(row.getEnabled())) {
        return row.getTitle() == null ? "" : row.getTitle();
      }
    } catch (RuntimeException ex) {
      log.warn("campaign table missing, fallback titles");
    }
    return CampaignCatalog.TITLES.getOrDefault(eventId.trim(), "");
  }

  public FeedDtos.PublicCatalog publicCatalog() {
    List<FeedDtos.OpsCategory> categories = toCategories(listCategories(), true);
    if (categories.isEmpty()) {
      int i = 1;
      for (String name : List.of("旅行", "美食", "时尚", "摄影", "生活", "运动", "游戏", "音乐")) {
        categories.add(new FeedDtos.OpsCategory("fb-" + i, name, i, true));
        i++;
      }
    }
    return new FeedDtos.PublicCatalog(
        categories,
        toCampaigns(listCampaigns(), true),
        toTopics(listTopics(null), true));
  }

  public FeedDtos.OpsCatalog opsCatalog() {
    return new FeedDtos.OpsCatalog(
        toCategories(listCategories(), false),
        toCampaigns(listCampaigns(), false),
        toTopics(listTopics(null), false));
  }

  public FeedDtos.ContentStats stats() {
    long live = postMapper.selectCount(
        Wrappers.<Post>lambdaQuery().isNull(Post::getDeletedAt).eq(Post::getStatus, "published"));
    long down = postMapper.selectCount(Wrappers.<Post>lambdaQuery().isNotNull(Post::getDeletedAt));
    long comments = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery().eq(Comment::getStatus, 0));
    return new FeedDtos.ContentStats(live, down, comments);
  }

  public List<FeedDtos.OpsTopic> suggestTopics(String q) {
    List<Topic> rows = listTopics(q);
    return toTopics(rows, true);
  }

  @Transactional
  public FeedDtos.OpsCategory saveCategory(String id, FeedDtos.CategorySpec spec) {
    if (spec == null || !StringUtils.hasText(spec.name())) {
      throw BusinessException.validation("分类名必填");
    }
    String name = spec.name().trim();
    if (name.length() > 32) {
      throw BusinessException.validation("分类名过长");
    }
    PostCategory row;
    if (StringUtils.hasText(id)) {
      row = categoryMapper.selectById(Long.parseLong(id));
      if (row == null) {
        throw BusinessException.notFound("分类不存在");
      }
    } else {
      row = new PostCategory();
      row.setId(IdGenerator.nextId());
      row.setCreatedAt(LocalDateTime.now());
    }
    assertNameFree(name, row.getId());
    row.setName(name);
    row.setSortOrder(spec.sortOrder() == null ? 0 : spec.sortOrder());
    row.setEnabled(bool(spec.enabled(), true));
    if (StringUtils.hasText(id)) {
      categoryMapper.updateById(row);
    } else {
      categoryMapper.insert(row);
    }
    return toCategory(row);
  }

  @Transactional
  public void setCategoryEnabled(long id, boolean enabled) {
    PostCategory row = categoryMapper.selectById(id);
    if (row == null) {
      throw BusinessException.notFound("分类不存在");
    }
    row.setEnabled(enabled ? 1 : 0);
    categoryMapper.updateById(row);
  }

  @Transactional
  public FeedDtos.OpsCampaign saveCampaign(FeedDtos.CampaignSpec spec, boolean create) {
    if (spec == null || !StringUtils.hasText(spec.eventId()) || !StringUtils.hasText(spec.title())) {
      throw BusinessException.validation("活动 id 和标题必填");
    }
    String eventId = spec.eventId().trim();
    if (!eventId.matches("[a-zA-Z0-9_-]{1,32}")) {
      throw BusinessException.validation("活动 id 只能字母数字下划线");
    }
    String status = spec.status() == null ? "live" : spec.status().trim();
    if (!CAMPAIGN_STATUS.contains(status)) {
      throw BusinessException.validation("status 须为 live / upcoming / ended");
    }
    Campaign row = campaignMapper.selectById(eventId);
    if (create) {
      if (row != null) {
        throw BusinessException.conflict("活动 id 已存在");
      }
      row = new Campaign();
      row.setId(eventId);
      row.setCreatedAt(LocalDateTime.now());
    } else if (row == null) {
      throw BusinessException.notFound("活动不存在");
    }
    row.setTitle(spec.title().trim());
    row.setIntro(spec.intro() == null ? "" : spec.intro().trim());
    row.setCoverUrl(normalizeCoverUrl(spec.coverUrl()));
    row.setStatus(status);
    row.setSortOrder(spec.sortOrder() == null ? 0 : spec.sortOrder());
    row.setEnabled(bool(spec.enabled(), true));
    if (create) {
      campaignMapper.insert(row);
    } else {
      campaignMapper.updateById(row);
    }
    return toCampaign(row);
  }

  @Transactional
  public void setCampaignEnabled(String eventId, boolean enabled) {
    Campaign row = campaignMapper.selectById(eventId);
    if (row == null) {
      throw BusinessException.notFound("活动不存在");
    }
    row.setEnabled(enabled ? 1 : 0);
    campaignMapper.updateById(row);
  }

  @Transactional
  public FeedDtos.OpsTopic saveTopic(String id, FeedDtos.TopicSpec spec) {
    if (spec == null || !StringUtils.hasText(spec.name())) {
      throw BusinessException.validation("话题名必填");
    }
    String name = spec.name().trim().replaceFirst("^#", "");
    if (name.isEmpty() || name.length() > 32) {
      throw BusinessException.validation("话题名不合法");
    }
    Topic row;
    if (StringUtils.hasText(id)) {
      row = topicMapper.selectById(Long.parseLong(id));
      if (row == null) {
        throw BusinessException.notFound("话题不存在");
      }
    } else {
      Topic existed = byTopicName(name);
      if (existed != null) {
        throw BusinessException.conflict("话题已存在");
      }
      row = new Topic();
      row.setId(IdGenerator.nextId());
      row.setCreatedAt(LocalDateTime.now());
    }
    row.setName(name);
    row.setOfficial(bool(spec.official(), true));
    row.setSortOrder(spec.sortOrder() == null ? 0 : spec.sortOrder());
    row.setEnabled(bool(spec.enabled(), true));
    if (StringUtils.hasText(id)) {
      topicMapper.updateById(row);
    } else {
      topicMapper.insert(row);
    }
    searchIndexer.syncTopic(row.getId());
    return toTopic(row);
  }

  @Transactional
  public void setTopicEnabled(long id, boolean enabled) {
    Topic row = topicMapper.selectById(id);
    if (row == null) {
      throw BusinessException.notFound("话题不存在");
    }
    row.setEnabled(enabled ? 1 : 0);
    topicMapper.updateById(row);
    searchIndexer.syncTopic(id);
  }

  @Transactional
  public void rememberHashtags(String body) {
    if (!StringUtils.hasText(body)) {
      return;
    }
    Matcher matcher = HASH.matcher(body);
    LinkedHashSet<String> names = new LinkedHashSet<>();
    while (matcher.find()) {
      names.add(matcher.group(1));
      if (names.size() >= 10) {
        break;
      }
    }
    try {
      for (String name : names) {
        if (byTopicName(name) != null) {
          continue;
        }
        Topic row = new Topic();
        row.setId(IdGenerator.nextId());
        row.setName(name);
        row.setOfficial(0);
        row.setSortOrder(0);
        row.setEnabled(1);
        row.setCreatedAt(LocalDateTime.now());
        topicMapper.insert(row);
        searchIndexer.syncTopic(row.getId());
      }
    } catch (RuntimeException ex) {
      log.warn("remember hashtags skipped");
    }
  }

  private void assertNameFree(String name, Long selfId) {
    PostCategory hit = categoryMapper.selectOne(
        Wrappers.<PostCategory>lambdaQuery().eq(PostCategory::getName, name).last("LIMIT 1"));
    if (hit != null && (selfId == null || !selfId.equals(hit.getId()))) {
      throw BusinessException.conflict("分类名已存在");
    }
  }

  public FeedDtos.TopicIndexPage listSearchableTopics(String cursor, Integer limit) {
    int size = limit == null ? 50 : Math.min(Math.max(limit, 1), 100);
    long after = 0L;
    if (StringUtils.hasText(cursor)) {
      try {
        after = Long.parseLong(cursor.trim());
      } catch (NumberFormatException ex) {
        after = 0L;
      }
    }
    List<Topic> rows = topicMapper.selectList(
        Wrappers.<Topic>lambdaQuery()
            .eq(Topic::getEnabled, 1)
            .gt(after > 0L, Topic::getId, after)
            .orderByAsc(Topic::getId)
            .last("LIMIT " + (size + 1)));
    if (rows == null) {
      rows = List.of();
    }
    boolean more = rows.size() > size;
    List<Topic> page = more ? rows.subList(0, size) : rows;
    List<FeedDtos.TopicIndexDoc> items = new ArrayList<>();
    for (Topic row : page) {
      items.add(new FeedDtos.TopicIndexDoc(
          String.valueOf(row.getId()),
          row.getName() == null ? "" : row.getName(),
          Integer.valueOf(1).equals(row.getOfficial()),
          n(row.getSortOrder())));
    }
    String next = more && !page.isEmpty() ? String.valueOf(page.getLast().getId()) : null;
    return new FeedDtos.TopicIndexPage(items, next, more);
  }

  private Topic byTopicName(String name) {
    return topicMapper.selectOne(Wrappers.<Topic>lambdaQuery().eq(Topic::getName, name).last("LIMIT 1"));
  }

  private List<PostCategory> listCategories() {
    try {
      List<PostCategory> rows = categoryMapper.selectList(
          Wrappers.<PostCategory>lambdaQuery().orderByAsc(PostCategory::getSortOrder).orderByAsc(PostCategory::getId));
      return rows == null ? List.of() : rows;
    } catch (RuntimeException ex) {
      log.warn("post_category missing, use fallback names");
      return List.of();
    }
  }

  private List<Campaign> listCampaigns() {
    try {
      List<Campaign> rows = campaignMapper.selectList(
          Wrappers.<Campaign>lambdaQuery().orderByAsc(Campaign::getSortOrder).orderByAsc(Campaign::getId));
      return rows == null ? List.of() : rows;
    } catch (RuntimeException ex) {
      log.warn("campaign table missing");
      return List.of();
    }
  }

  private List<Topic> listTopics(String q) {
    try {
      var query = Wrappers.<Topic>lambdaQuery().orderByDesc(Topic::getOfficial)
          .orderByAsc(Topic::getSortOrder).orderByDesc(Topic::getId);
      if (StringUtils.hasText(q)) {
        query.like(Topic::getName, q.trim().replaceFirst("^#", ""));
      }
      List<Topic> rows = topicMapper.selectList(query.last("LIMIT 100"));
      return rows == null ? List.of() : rows;
    } catch (RuntimeException ex) {
      log.warn("topic table missing");
      return List.of();
    }
  }

  private static List<FeedDtos.OpsCategory> toCategories(List<PostCategory> rows, boolean enabledOnly) {
    List<FeedDtos.OpsCategory> out = new ArrayList<>();
    for (PostCategory row : rows) {
      if (enabledOnly && !enabled(row.getEnabled())) {
        continue;
      }
      out.add(toCategory(row));
    }
    return out;
  }

  private static FeedDtos.OpsCategory toCategory(PostCategory row) {
    return new FeedDtos.OpsCategory(
        String.valueOf(row.getId()),
        row.getName(),
        n(row.getSortOrder()),
        enabled(row.getEnabled()));
  }

  private static List<FeedDtos.OpsCampaign> toCampaigns(List<Campaign> rows, boolean enabledOnly) {
    List<FeedDtos.OpsCampaign> out = new ArrayList<>();
    for (Campaign row : rows) {
      if (enabledOnly && !enabled(row.getEnabled())) {
        continue;
      }
      out.add(toCampaign(row));
    }
    return out;
  }

  private static FeedDtos.OpsCampaign toCampaign(Campaign row) {
    return new FeedDtos.OpsCampaign(
        row.getId(),
        row.getTitle() == null ? "" : row.getTitle(),
        row.getIntro() == null ? "" : row.getIntro(),
        row.getCoverUrl() == null ? "" : row.getCoverUrl(),
        row.getStatus() == null ? "live" : row.getStatus(),
        n(row.getSortOrder()),
        enabled(row.getEnabled()));
  }

  private static List<FeedDtos.OpsTopic> toTopics(List<Topic> rows, boolean enabledOnly) {
    List<FeedDtos.OpsTopic> out = new ArrayList<>();
    for (Topic row : rows) {
      if (enabledOnly && !enabled(row.getEnabled())) {
        continue;
      }
      out.add(toTopic(row));
    }
    return out;
  }

  private static FeedDtos.OpsTopic toTopic(Topic row) {
    return new FeedDtos.OpsTopic(
        String.valueOf(row.getId()),
        row.getName(),
        Integer.valueOf(1).equals(row.getOfficial()),
        n(row.getSortOrder()),
        enabled(row.getEnabled()));
  }

  private static boolean enabled(Integer value) {
    return value == null || value == 1;
  }

  private static int bool(Boolean value, boolean fallback) {
    boolean on = value == null ? fallback : value;
    return on ? 1 : 0;
  }

  private static int n(Integer value) {
    return value == null ? 0 : value;
  }

  /** 只存公开对象地址。预签名 query 会超过 cover_url 512 并让 MySQL 500。 */
  private static String normalizeCoverUrl(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "";
    }
    String text = raw.trim();
    if (text.startsWith("blob:")) {
      throw BusinessException.validation("封面还没上传完");
    }
    int query = text.indexOf('?');
    if (query >= 0) {
      text = text.substring(0, query);
    }
    if (text.length() > 512) {
      throw BusinessException.validation("封面地址过长");
    }
    return text;
  }
}
