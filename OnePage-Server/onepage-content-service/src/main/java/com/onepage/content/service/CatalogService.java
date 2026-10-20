package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.entity.Campaign;
import com.onepage.content.entity.Comment;
import com.onepage.content.entity.Post;
import com.onepage.content.entity.PostCategory;
import com.onepage.content.entity.CreatorFaq;
import com.onepage.content.entity.PlatformNotice;
import com.onepage.content.entity.Topic;
import com.onepage.content.mapper.CampaignMapper;
import com.onepage.content.mapper.CommentMapper;
import com.onepage.content.mapper.CreatorFaqMapper;
import com.onepage.content.mapper.PlatformNoticeMapper;
import com.onepage.content.mapper.PostCategoryMapper;
import com.onepage.content.mapper.PostMapper;
import com.onepage.content.mapper.TopicMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final Set<String> CAMPAIGN_STATUS = Set.of("live", "upcoming", "ended");
  private static final Map<String, String> FAQ_GROUPS = Map.of(
      "account", "用户账号管理",
      "create", "创作功能",
      "data", "数据中心",
      "faq", "常见问题");

  private final PostCategoryMapper categoryMapper;
  private final CampaignMapper campaignMapper;
  private final TopicMapper topicMapper;
  private final PlatformNoticeMapper noticeMapper;
  private final CreatorFaqMapper faqMapper;
  private final PostMapper postMapper;
  private final CommentMapper commentMapper;
  private final SearchIndexer searchIndexer;

  public CatalogService(
      PostCategoryMapper categoryMapper,
      CampaignMapper campaignMapper,
      TopicMapper topicMapper,
      PlatformNoticeMapper noticeMapper,
      CreatorFaqMapper faqMapper,
      PostMapper postMapper,
      CommentMapper commentMapper,
      @Lazy SearchIndexer searchIndexer) {
    this.categoryMapper = categoryMapper;
    this.campaignMapper = campaignMapper;
    this.topicMapper = topicMapper;
    this.noticeMapper = noticeMapper;
    this.faqMapper = faqMapper;
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
        toTopics(listTopics(null), true),
        toNotices(listNotices(), true));
  }

  public FeedDtos.OpsCatalog opsCatalog() {
    return new FeedDtos.OpsCatalog(
        toCategories(listCategories(), false),
        toCampaigns(listCampaigns(), false),
        toTopics(listTopics(null), false),
        toNotices(listNotices(), false));
  }

  public FeedDtos.ContentStats stats() {
    LocalDateTime today = LocalDate.now(ZONE).atStartOfDay();
    long live = n(postMapper.selectCount(
        Wrappers.<Post>lambdaQuery().isNull(Post::getDeletedAt).eq(Post::getStatus, "published")));
    long down = n(postMapper.selectCount(Wrappers.<Post>lambdaQuery().isNotNull(Post::getDeletedAt)));
    long pending = n(postMapper.selectCount(
        Wrappers.<Post>lambdaQuery().isNull(Post::getDeletedAt).eq(Post::getStatus, "pending")));
    long rejected = n(postMapper.selectCount(
        Wrappers.<Post>lambdaQuery().isNull(Post::getDeletedAt).eq(Post::getStatus, "rejected")));
    long comments = n(commentMapper.selectCount(Wrappers.<Comment>lambdaQuery().eq(Comment::getStatus, 0)));
    long postsToday = n(postMapper.selectCount(Wrappers.<Post>lambdaQuery().ge(Post::getCreatedAt, today)));
    long commentsToday = n(commentMapper.selectCount(
        Wrappers.<Comment>lambdaQuery().ge(Comment::getCreatedAt, today)));
    return new FeedDtos.ContentStats(live, down, pending, rejected, comments, postsToday, commentsToday);
  }

  public List<FeedDtos.OpsTopic> suggestTopics(String q) {
    List<Topic> rows = listTopics(q);
    return toTopics(rows, true);
  }

  public List<FeedDtos.OpsFaq> publicFaqs() {
    return toFaqs(listFaqs(), true);
  }

  public List<FeedDtos.OpsFaq> opsFaqs() {
    return toFaqs(listFaqs(), false);
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
  public FeedDtos.OpsNotice saveNotice(String id, FeedDtos.NoticeSpec spec) {
    if (spec == null || !StringUtils.hasText(spec.title())) {
      throw BusinessException.validation("公告标题必填");
    }
    String title = spec.title().trim();
    if (title.length() > 64) {
      throw BusinessException.validation("公告标题过长");
    }
    String body = spec.body() == null ? "" : spec.body().trim();
    if (body.length() > 500) {
      throw BusinessException.validation("公告正文过长");
    }
    PlatformNotice row;
    if (StringUtils.hasText(id)) {
      row = noticeMapper.selectById(Long.parseLong(id));
      if (row == null) {
        throw BusinessException.notFound("公告不存在");
      }
    } else {
      row = new PlatformNotice();
      row.setId(IdGenerator.nextId());
      row.setCreatedAt(LocalDateTime.now());
    }
    row.setTitle(title);
    row.setBody(body);
    row.setSortOrder(spec.sortOrder() == null ? 0 : spec.sortOrder());
    row.setEnabled(bool(spec.enabled(), true));
    if (StringUtils.hasText(id)) {
      noticeMapper.updateById(row);
    } else {
      noticeMapper.insert(row);
    }
    return toNotice(row);
  }

  @Transactional
  public void setNoticeEnabled(long id, boolean enabled) {
    PlatformNotice row = noticeMapper.selectById(id);
    if (row == null) {
      throw BusinessException.notFound("公告不存在");
    }
    row.setEnabled(enabled ? 1 : 0);
    noticeMapper.updateById(row);
  }

  @Transactional
  public FeedDtos.OpsFaq saveFaq(String id, FeedDtos.FaqSpec spec) {
    if (spec == null || !StringUtils.hasText(spec.groupId()) || !StringUtils.hasText(spec.question())
        || !StringUtils.hasText(spec.answer())) {
      throw BusinessException.validation("分组、问题和回答必填");
    }
    String groupId = spec.groupId().trim();
    if (!FAQ_GROUPS.containsKey(groupId)) {
      throw BusinessException.validation("分组不合法");
    }
    String question = spec.question().trim();
    String answer = spec.answer().trim();
    if (question.length() > 120) {
      throw BusinessException.validation("问题过长");
    }
    if (answer.length() > 1000) {
      throw BusinessException.validation("回答过长");
    }
    CreatorFaq row;
    if (StringUtils.hasText(id)) {
      row = faqMapper.selectById(Long.parseLong(id));
      if (row == null) {
        throw BusinessException.notFound("问答不存在");
      }
    } else {
      row = new CreatorFaq();
      row.setId(IdGenerator.nextId());
      row.setCreatedAt(LocalDateTime.now());
    }
    row.setGroupId(groupId);
    row.setQuestion(question);
    row.setAnswer(answer);
    row.setSortOrder(spec.sortOrder() == null ? 0 : spec.sortOrder());
    row.setEnabled(bool(spec.enabled(), true));
    if (StringUtils.hasText(id)) {
      faqMapper.updateById(row);
    } else {
      faqMapper.insert(row);
    }
    return toFaq(row);
  }

  @Transactional
  public void setFaqEnabled(long id, boolean enabled) {
    CreatorFaq row = faqMapper.selectById(id);
    if (row == null) {
      throw BusinessException.notFound("问答不存在");
    }
    row.setEnabled(enabled ? 1 : 0);
    faqMapper.updateById(row);
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

  private List<PlatformNotice> listNotices() {
    try {
      List<PlatformNotice> rows = noticeMapper.selectList(
          Wrappers.<PlatformNotice>lambdaQuery()
              .orderByAsc(PlatformNotice::getSortOrder)
              .orderByAsc(PlatformNotice::getId));
      return rows == null ? List.of() : rows;
    } catch (RuntimeException ex) {
      log.warn("platform_notice missing");
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

  private List<FeedDtos.OpsTopic> toTopics(List<Topic> rows, boolean enabledOnly) {
    Map<Long, Long> counts = topicCounts();
    List<FeedDtos.OpsTopic> out = new ArrayList<>();
    for (Topic row : rows) {
      if (enabledOnly && !enabled(row.getEnabled())) {
        continue;
      }
      out.add(toTopic(row, counts.getOrDefault(row.getId(), 0L)));
    }
    return out;
  }

  private FeedDtos.OpsTopic toTopic(Topic row) {
    long count = 0L;
    if (row.getId() != null) {
      count = topicCounts().getOrDefault(row.getId(), 0L);
    }
    return toTopic(row, count);
  }

  private static FeedDtos.OpsTopic toTopic(Topic row, long postCount) {
    return new FeedDtos.OpsTopic(
        String.valueOf(row.getId()),
        row.getName(),
        Integer.valueOf(1).equals(row.getOfficial()),
        n(row.getSortOrder()),
        enabled(row.getEnabled()),
        postCount);
  }

  private Map<Long, Long> topicCounts() {
    Map<Long, Long> map = new HashMap<>();
    try {
      List<TopicMapper.CountRow> rows = topicMapper.countPostsByTopic();
      if (rows != null) {
        for (TopicMapper.CountRow row : rows) {
          if (row != null) {
            map.put(row.topicId(), row.cnt());
          }
        }
      }
    } catch (RuntimeException ex) {
      log.warn("topic post counts unavailable");
    }
    return map;
  }

  private static List<FeedDtos.OpsNotice> toNotices(List<PlatformNotice> rows, boolean enabledOnly) {
    List<FeedDtos.OpsNotice> out = new ArrayList<>();
    for (PlatformNotice row : rows) {
      if (enabledOnly && !enabled(row.getEnabled())) {
        continue;
      }
      out.add(toNotice(row));
    }
    return out;
  }

  private List<CreatorFaq> listFaqs() {
    try {
      List<CreatorFaq> rows = faqMapper.selectList(
          Wrappers.<CreatorFaq>lambdaQuery()
              .orderByAsc(CreatorFaq::getGroupId)
              .orderByAsc(CreatorFaq::getSortOrder)
              .orderByAsc(CreatorFaq::getId));
      return rows == null ? List.of() : rows;
    } catch (RuntimeException ex) {
      log.warn("creator_faq missing");
      return List.of();
    }
  }

  private static List<FeedDtos.OpsFaq> toFaqs(List<CreatorFaq> rows, boolean enabledOnly) {
    List<FeedDtos.OpsFaq> out = new ArrayList<>();
    for (CreatorFaq row : rows) {
      if (enabledOnly && !enabled(row.getEnabled())) {
        continue;
      }
      out.add(toFaq(row));
    }
    return out;
  }

  private static FeedDtos.OpsFaq toFaq(CreatorFaq row) {
    String groupId = row.getGroupId() == null ? "" : row.getGroupId();
    return new FeedDtos.OpsFaq(
        String.valueOf(row.getId()),
        groupId,
        FAQ_GROUPS.getOrDefault(groupId, groupId),
        row.getQuestion() == null ? "" : row.getQuestion(),
        row.getAnswer() == null ? "" : row.getAnswer(),
        n(row.getSortOrder()),
        enabled(row.getEnabled()));
  }

  private static FeedDtos.OpsNotice toNotice(PlatformNotice row) {
    return new FeedDtos.OpsNotice(
        String.valueOf(row.getId()),
        row.getTitle() == null ? "" : row.getTitle(),
        row.getBody() == null ? "" : row.getBody(),
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

  private static long n(Long value) {
    return value == null ? 0L : value;
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
