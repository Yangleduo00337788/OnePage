package com.onepage.moderation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.moderation.client.AuthClient;
import com.onepage.moderation.client.ContentClient;
import com.onepage.moderation.client.LiveClient;
import com.onepage.moderation.client.MessageClient;
import com.onepage.moderation.client.NotifyClient;
import com.onepage.moderation.client.UserClient;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.entity.Report;
import com.onepage.moderation.mapper.ReportMapper;
import com.onepage.moderation.web.StaffGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 举报入库与处理。下架/冻结用 Feign，失败不把工单标成已处理。
 */
@Service
public class ModerationService {
  private static final Logger log = LoggerFactory.getLogger(ModerationService.class);
  private static final Set<String> TARGETS = Set.of("post", "user", "comment", "live");
  private static final Set<String> ACTIONS = Set.of("dismiss", "takedown", "restore", "ban", "unban");
  private static final Set<String> REASONS = Set.of("spam", "porn", "abuse", "illegal", "other");
  private static final int STATUS_PENDING = 0;
  private static final int STATUS_DONE = 1;
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private final ReportMapper mapper;
  private final UserClient userClient;
  private final ContentClient contentClient;
  private final AuthClient authClient;
  private final MessageClient messageClient;
  private final NotifyClient notifyClient;
  private final LiveClient liveClient;
  private final SupportService supportService;
  private final StaffGuard staffGuard;
  private final String internalToken;

  public ModerationService(
      ReportMapper mapper,
      UserClient userClient,
      ContentClient contentClient,
      AuthClient authClient,
      MessageClient messageClient,
      NotifyClient notifyClient,
      LiveClient liveClient,
      SupportService supportService,
      StaffGuard staffGuard,
      @Value("${onepage.internal-token}") String internalToken) {
    this.mapper = mapper;
    this.userClient = userClient;
    this.contentClient = contentClient;
    this.authClient = authClient;
    this.messageClient = messageClient;
    this.notifyClient = notifyClient;
    this.liveClient = liveClient;
    this.supportService = supportService;
    this.staffGuard = staffGuard;
    this.internalToken = internalToken;
  }

  public ModerationDtos.CreateReportResult create(long reporterId, ModerationDtos.CreateReportRequest body) {
    if (body == null || !StringUtils.hasText(body.targetType()) || !StringUtils.hasText(body.targetId())
        || !StringUtils.hasText(body.reasonCode())) {
      throw BusinessException.validation("举报字段不完整");
    }
    String type = body.targetType().trim();
    if (!TARGETS.contains(type)) {
      throw BusinessException.validation("targetType 须为 post、user、comment 或 live");
    }
    long targetId = parseId(body.targetId(), "targetId");
    if ("user".equals(type) && targetId == reporterId) {
      throw BusinessException.validation("不能举报自己");
    }
    String reason = body.reasonCode().trim();
    if (!REASONS.contains(reason)) {
      throw BusinessException.validation("原因须为 spam / porn / abuse / illegal / other");
    }
    String detail = blankToNull(body.detail());
    if (detail != null && detail.length() > 1000) {
      throw BusinessException.validation("说明过长");
    }
    Report row = new Report();
    long id = IdGenerator.nextId();
    row.setId(id);
    row.setReporterId(reporterId);
    row.setTargetType(type);
    row.setTargetId(targetId);
    row.setReasonCode(reason);
    row.setDetail(detail);
    row.setStatus(STATUS_PENDING);
    row.setCreatedAt(LocalDateTime.now());
    mapper.insert(row);
    return new ModerationDtos.CreateReportResult(String.valueOf(id));
  }

  public ModerationDtos.CursorPage page(String status, String cursor, Integer limit) {
    Integer statusCode = parseStatus(status);
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LocalDateTime cursorAt = null;
    Long cursorId = null;
    if (StringUtils.hasText(cursor)) {
      String[] parts = cursor.split("_", 2);
      if (parts.length != 2) {
        throw BusinessException.validation("cursor 不合法");
      }
      try {
        cursorAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneId.systemDefault());
        cursorId = Long.parseLong(parts[1]);
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("cursor 不合法");
      }
    }
    List<Report> rows = mapper.page(statusCode, cursorAt, cursorId, size + 1);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    Map<Long, ContentClient.PostBrief> posts = loadPosts(rows);
    Map<Long, ContentClient.OpsCommentRow> comments = loadComments(rows);
    Map<Long, LiveClient.LiveItem> lives = loadLives(rows);
    Map<Long, UserSummary> people = loadPeople(rows, posts, comments, lives);
    List<ModerationDtos.OpsReport> items = new ArrayList<>();
    for (Report row : rows) {
      items.add(toView(row, people, posts, comments, lives));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      Report last = rows.getLast();
      if (last.getCreatedAt() != null && last.getId() != null) {
        next = last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + "_" + last.getId();
      }
    }
    return new ModerationDtos.CursorPage(items, next, hasMore);
  }

  @Transactional
  public ModerationDtos.ResolveResult resolve(long handlerId, long reportId, ModerationDtos.ResolveRequest body) {
    if (body == null || !StringUtils.hasText(body.action())) {
      throw BusinessException.validation("action 必填");
    }
    String action = body.action().trim();
    if (!ACTIONS.contains(action)) {
      throw BusinessException.validation("action 须为 dismiss / takedown / restore / ban / unban");
    }
    String note = blankToNull(body.note());
    if (note != null && note.length() > 500) {
      throw BusinessException.validation("备注过长");
    }
    Report row = mapper.selectById(reportId);
    if (row == null) {
      throw BusinessException.notFound("工单不存在");
    }
    boolean alreadyDone = row.getStatus() != null && row.getStatus() == STATUS_DONE;
    if (alreadyDone && "dismiss".equals(action)) {
      throw BusinessException.conflict("工单已处理");
    }
    applyAction(handlerId, row, action);
    LocalDateTime now = LocalDateTime.now();
    row.setStatus(STATUS_DONE);
    row.setHandlerId(handlerId);
    row.setHandleAction(action);
    row.setHandleNote(note);
    row.setHandledAt(now);
    mapper.updateById(row);
    log.warn("ops report {} action {} by {}", reportId, action, handlerId);
    return new ModerationDtos.ResolveResult(String.valueOf(reportId), "done");
  }

  public ModerationDtos.OpsPostView opsPost(long postId) {
    ContentClient.OpsPostView raw = contentClient.opsPost(internalToken, postId);
    if (raw == null) {
      throw BusinessException.notFound("笔记不存在");
    }
    List<ModerationDtos.ContentMedia> media = new ArrayList<>();
    if (raw.media() != null) {
      for (ContentClient.MediaItem item : raw.media()) {
        if (item == null) {
          continue;
        }
        media.add(new ModerationDtos.ContentMedia(item.mediaId(), item.url(), item.mimeType(), item.role()));
      }
    }
    return new ModerationDtos.OpsPostView(
        raw.postId(),
        raw.author(),
        raw.title() == null ? "" : raw.title(),
        raw.body() == null ? "" : raw.body(),
        raw.coverUrl() == null ? "" : raw.coverUrl(),
        raw.postType() == null ? "" : raw.postType(),
        raw.category() == null ? "" : raw.category(),
        raw.original(),
        raw.contentKind() == null ? "" : raw.contentKind(),
        raw.eventTitle() == null ? "" : raw.eventTitle(),
        raw.visibility() == null ? "" : raw.visibility(),
        media,
        raw.takenDown(),
        raw.publishedAt() == null ? "" : raw.publishedAt(),
        raw.reviewStatus() == null ? "" : raw.reviewStatus(),
        raw.rejectReason() == null ? "" : raw.rejectReason(),
        raw.aiReview() == null ? "" : raw.aiReview(),
        raw.aiReviewReason() == null ? "" : raw.aiReviewReason(),
        raw.aiReviewScore());
  }

  public ModerationDtos.OpsPostPage opsPosts(String q, String taken, String cursor, Integer limit) {
    ContentClient.OpsPostPage page = contentClient.opsPosts(internalToken, q, taken, cursor, limit);
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsPostPage(List.of(), null, false);
    }
    List<ModerationDtos.OpsPostRow> items = new ArrayList<>();
    for (ContentClient.OpsPostRow row : page.items()) {
      if (row == null) {
        continue;
      }
      items.add(new ModerationDtos.OpsPostRow(
          row.postId(),
          row.author(),
          row.title() == null ? "" : row.title(),
          row.postType() == null ? "" : row.postType(),
          row.category() == null ? "" : row.category(),
          row.takenDown(),
          row.publishedAt() == null ? "" : row.publishedAt(),
          row.reviewStatus() == null ? "" : row.reviewStatus(),
          row.rejectReason() == null ? "" : row.rejectReason(),
          row.aiReview() == null ? "" : row.aiReview(),
          row.aiReviewReason() == null ? "" : row.aiReviewReason(),
          row.aiReviewScore()));
    }
    return new ModerationDtos.OpsPostPage(items, page.nextCursor(), page.hasMore());
  }

  public ModerationDtos.OpsPostView takedownPost(long postId) {
    contentClient.takedown(internalToken, postId);
    return opsPost(postId);
  }

  public ModerationDtos.OpsPostView restorePost(long postId) {
    contentClient.restore(internalToken, postId);
    return opsPost(postId);
  }

  public ModerationDtos.OpsPostView approvePost(long postId) {
    contentClient.approve(internalToken, postId);
    return opsPost(postId);
  }

  public ModerationDtos.OpsPostView rejectPost(long postId, String reason, long handlerId) {
    contentClient.reject(internalToken, postId, reason, handlerId);
    return opsPost(postId);
  }

  public ModerationDtos.OpsCommentPage opsComments(String q, String hidden, String cursor, Integer limit) {
    ContentClient.OpsCommentPage page = contentClient.opsComments(internalToken, q, hidden, cursor, limit);
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsCommentPage(List.of(), null, false);
    }
    List<ModerationDtos.OpsCommentRow> items = new ArrayList<>();
    for (ContentClient.OpsCommentRow row : page.items()) {
      if (row == null) {
        continue;
      }
      items.add(new ModerationDtos.OpsCommentRow(
          row.commentId(),
          row.postId() == null ? "" : row.postId(),
          row.author(),
          row.content() == null ? "" : row.content(),
          row.hidden(),
          row.createdAt() == null ? "" : row.createdAt()));
    }
    return new ModerationDtos.OpsCommentPage(items, page.nextCursor(), page.hasMore());
  }

  public ModerationDtos.OpsMediaPage opsMedia(
      String q, String purpose, String status, String hidden, String cursor, Integer limit) {
    ContentClient.OpsMediaPage page =
        contentClient.opsMedia(internalToken, q, purpose, status, hidden, cursor, limit);
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsMediaPage(List.of(), null, false);
    }
    List<ModerationDtos.OpsMediaRow> items = new ArrayList<>();
    for (ContentClient.OpsMediaRow row : page.items()) {
      if (row == null) {
        continue;
      }
      items.add(new ModerationDtos.OpsMediaRow(
          row.mediaId(),
          row.owner(),
          row.purpose() == null ? "" : row.purpose(),
          row.mimeType() == null ? "" : row.mimeType(),
          row.sizeBytes(),
          row.status() == null ? "" : row.status(),
          row.url() == null ? "" : row.url(),
          row.postId() == null ? "" : row.postId(),
          row.hidden(),
          row.createdAt() == null ? "" : row.createdAt()));
    }
    return new ModerationDtos.OpsMediaPage(items, page.nextCursor(), page.hasMore());
  }

  public void hideMedia(long mediaId) {
    contentClient.hideMedia(internalToken, mediaId);
  }

  public void restoreMedia(long mediaId) {
    contentClient.restoreMedia(internalToken, mediaId);
  }

  public void hideComment(long commentId) {
    contentClient.hideComment(internalToken, commentId);
  }

  public void restoreComment(long commentId) {
    contentClient.restoreComment(internalToken, commentId);
  }

  public ModerationDtos.OpsCatalog catalog() {
    ContentClient.OpsCatalog raw = contentClient.catalog(internalToken);
    if (raw == null) {
      return new ModerationDtos.OpsCatalog(List.of(), List.of(), List.of(), List.of());
    }
    List<ModerationDtos.OpsCategory> categories = new ArrayList<>();
    if (raw.categories() != null) {
      for (ContentClient.OpsCategory row : raw.categories()) {
        if (row != null) {
          categories.add(new ModerationDtos.OpsCategory(row.categoryId(), row.name(), row.sortOrder(), row.enabled()));
        }
      }
    }
    List<ModerationDtos.OpsCampaign> campaigns = new ArrayList<>();
    if (raw.campaigns() != null) {
      for (ContentClient.OpsCampaign row : raw.campaigns()) {
        if (row != null) {
          campaigns.add(new ModerationDtos.OpsCampaign(
              row.eventId(), row.title(), row.intro(), row.coverUrl(), row.status(), row.sortOrder(), row.enabled()));
        }
      }
    }
    List<ModerationDtos.OpsTopic> topics = new ArrayList<>();
    if (raw.topics() != null) {
      for (ContentClient.OpsTopic row : raw.topics()) {
        if (row != null) {
          topics.add(new ModerationDtos.OpsTopic(
              row.topicId(), row.name(), row.official(), row.sortOrder(), row.enabled(), row.postCount()));
        }
      }
    }
    List<ModerationDtos.OpsNotice> notices = new ArrayList<>();
    if (raw.notices() != null) {
      for (ContentClient.OpsNotice row : raw.notices()) {
        if (row != null) {
          notices.add(new ModerationDtos.OpsNotice(
              row.noticeId(), row.title(), row.body(), row.sortOrder(), row.enabled()));
        }
      }
    }
    return new ModerationDtos.OpsCatalog(categories, campaigns, topics, notices);
  }

  public ModerationDtos.OpsOverview overview() {
    long users = 0;
    long usersToday = 0;
    try {
      UserClient.Count c = userClient.count(internalToken);
      if (c != null) {
        users = c.users();
        usersToday = c.usersToday();
      }
    } catch (RuntimeException ex) {
      log.warn("ops user-count failed");
    }
    long live = 0;
    long down = 0;
    long postsPending = 0;
    long postsRejected = 0;
    long postsToday = 0;
    long comments = 0;
    long commentsToday = 0;
    try {
      ContentClient.ContentStats st = contentClient.stats(internalToken);
      if (st != null) {
        live = st.postsLive();
        down = st.postsDown();
        postsPending = st.postsPending();
        postsRejected = st.postsRejected();
        postsToday = st.postsToday();
        comments = st.comments();
        commentsToday = st.commentsToday();
      }
    } catch (RuntimeException ex) {
      log.warn("ops content-stats failed");
    }
    ModerationDtos.OpsCatalog cat = catalog();
    LocalDateTime today = LocalDate.now(ZONE).atStartOfDay();
    long pending = n(mapper.selectCount(Wrappers.<Report>lambdaQuery().eq(Report::getStatus, STATUS_PENDING)));
    long done = n(mapper.selectCount(Wrappers.<Report>lambdaQuery().eq(Report::getStatus, STATUS_DONE)));
    long reportsToday = n(mapper.selectCount(Wrappers.<Report>lambdaQuery().ge(Report::getCreatedAt, today)));
    long supportPending = 0;
    try {
      supportPending = supportService.countPending();
    } catch (RuntimeException ex) {
      log.warn("ops support-count failed");
    }
    return new ModerationDtos.OpsOverview(
        users,
        usersToday,
        live,
        down,
        postsPending,
        postsRejected,
        postsToday,
        comments,
        commentsToday,
        pending,
        done,
        reportsToday,
        supportPending,
        cat.categories().size(),
        cat.campaigns().size(),
        cat.topics().size());
  }

  public ModerationDtos.OpsCategory saveCategory(String id, ModerationDtos.CategorySpec spec) {
    ContentClient.CategorySpec body = new ContentClient.CategorySpec(spec.name(), spec.sortOrder(), spec.enabled());
    ContentClient.OpsCategory row = StringUtils.hasText(id)
        ? contentClient.patchCategory(internalToken, id, body)
        : contentClient.createCategory(internalToken, body);
    return new ModerationDtos.OpsCategory(row.categoryId(), row.name(), row.sortOrder(), row.enabled());
  }

  public void setCategoryEnabled(long id, boolean enabled) {
    contentClient.categoryEnabled(internalToken, id, enabled);
  }

  public ModerationDtos.OpsCampaign saveCampaign(ModerationDtos.CampaignSpec spec, boolean create) {
    ContentClient.CampaignSpec body = new ContentClient.CampaignSpec(
        spec.eventId(), spec.title(), spec.intro(), spec.coverUrl(), spec.status(), spec.sortOrder(), spec.enabled());
    ContentClient.OpsCampaign row = create
        ? contentClient.createCampaign(internalToken, body)
        : contentClient.patchCampaign(internalToken, spec.eventId(), body);
    return new ModerationDtos.OpsCampaign(
        row.eventId(), row.title(), row.intro(), row.coverUrl(), row.status(), row.sortOrder(), row.enabled());
  }

  public void setCampaignEnabled(String eventId, boolean enabled) {
    contentClient.campaignEnabled(internalToken, eventId, enabled);
  }

  public ModerationDtos.OpsTopic saveTopic(String id, ModerationDtos.TopicSpec spec) {
    ContentClient.TopicSpec body = new ContentClient.TopicSpec(spec.name(), spec.official(), spec.sortOrder(), spec.enabled());
    ContentClient.OpsTopic row = StringUtils.hasText(id)
        ? contentClient.patchTopic(internalToken, id, body)
        : contentClient.createTopic(internalToken, body);
    return new ModerationDtos.OpsTopic(
        row.topicId(), row.name(), row.official(), row.sortOrder(), row.enabled(), row.postCount());
  }

  public void setTopicEnabled(long id, boolean enabled) {
    contentClient.topicEnabled(internalToken, id, enabled);
  }

  public ModerationDtos.OpsNotice saveNotice(String id, ModerationDtos.NoticeSpec spec) {
    ContentClient.NoticeSpec body =
        new ContentClient.NoticeSpec(spec.title(), spec.body(), spec.sortOrder(), spec.enabled());
    ContentClient.OpsNotice row = StringUtils.hasText(id)
        ? contentClient.patchNotice(internalToken, id, body)
        : contentClient.createNotice(internalToken, body);
    return new ModerationDtos.OpsNotice(row.noticeId(), row.title(), row.body(), row.sortOrder(), row.enabled());
  }

  public void setNoticeEnabled(long id, boolean enabled) {
    contentClient.noticeEnabled(internalToken, id, enabled);
  }

  public List<ModerationDtos.OpsFaq> listFaqs() {
    List<ContentClient.OpsFaq> raw = contentClient.faqs(internalToken);
    List<ModerationDtos.OpsFaq> out = new ArrayList<>();
    if (raw == null) {
      return out;
    }
    for (ContentClient.OpsFaq row : raw) {
      if (row != null) {
        out.add(toFaq(row));
      }
    }
    return out;
  }

  public ModerationDtos.OpsFaq saveFaq(String id, ModerationDtos.FaqSpec spec) {
    ContentClient.FaqSpec body = new ContentClient.FaqSpec(
        spec.groupId(), spec.question(), spec.answer(), spec.sortOrder(), spec.enabled());
    ContentClient.OpsFaq row = StringUtils.hasText(id)
        ? contentClient.patchFaq(internalToken, id, body)
        : contentClient.createFaq(internalToken, body);
    return toFaq(row);
  }

  public void setFaqEnabled(long id, boolean enabled) {
    contentClient.faqEnabled(internalToken, id, enabled);
  }

  private static ModerationDtos.OpsUser toOpsUser(long targetId, UserSummary brief, boolean frozen) {
    if (brief == null) {
      return new ModerationDtos.OpsUser(String.valueOf(targetId), "", "", "", false, false, false, frozen);
    }
    return new ModerationDtos.OpsUser(
        brief.userId(),
        brief.pageId(),
        brief.nickname(),
        brief.avatarUrl(),
        brief.liveHost(),
        brief.verified(),
        brief.official(),
        frozen);
  }

  private static ModerationDtos.OpsFaq toFaq(ContentClient.OpsFaq row) {
    return new ModerationDtos.OpsFaq(
        row.faqId(), row.groupId(), row.groupTitle(), row.question(), row.answer(), row.sortOrder(), row.enabled());
  }

  public ModerationDtos.OpsUser getUser(long targetId) {
    List<UserSummary> briefs = userClient.briefs(internalToken, List.of(targetId));
    UserSummary brief = briefs == null || briefs.isEmpty() ? null : briefs.get(0);
    boolean frozen = false;
    try {
      AuthClient.FreezeStatus st = authClient.freezeStatus(internalToken, targetId);
      frozen = st != null && st.frozen();
    } catch (RuntimeException ex) {
      log.warn("ops freeze-status {} failed", targetId);
    }
    if (brief == null) {
      return toOpsUser(targetId, null, frozen);
    }
    return toOpsUser(targetId, brief, frozen);
  }

  public ModerationDtos.OpsUserPage pageUsers(String q, String cursor, Integer limit) {
    UserClient.UserPage page = userClient.opsUsers(internalToken, q, cursor, limit);
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsUserPage(List.of(), null, false);
    }
    List<ModerationDtos.OpsUser> out = new ArrayList<>();
    for (UserSummary row : page.items()) {
      if (row == null || !StringUtils.hasText(row.userId())) {
        continue;
      }
      boolean frozen = false;
      try {
        AuthClient.FreezeStatus st = authClient.freezeStatus(internalToken, Long.parseLong(row.userId()));
        frozen = st != null && st.frozen();
      } catch (RuntimeException ex) {
        log.warn("ops freeze-status {} failed", row.userId());
      }
      out.add(toOpsUser(Long.parseLong(row.userId()), row, frozen));
    }
    return new ModerationDtos.OpsUserPage(out, page.nextCursor(), page.hasMore());
  }

  public ModerationDtos.OpsUser freezeUser(long actorId, long targetId, boolean frozen) {
    assertCanPunishUser(actorId, targetId);
    if (frozen) {
      authClient.freeze(internalToken, targetId);
    } else {
      authClient.unfreeze(internalToken, targetId);
    }
    List<UserSummary> briefs = userClient.briefs(internalToken, List.of(targetId));
    UserSummary brief = briefs == null || briefs.isEmpty() ? null : briefs.get(0);
    if (brief == null) {
      return toOpsUser(targetId, null, frozen);
    }
    return toOpsUser(targetId, brief, frozen);
  }

  public ModerationDtos.OpsUser setUserBadges(long actorId, long targetId, ModerationDtos.SetBadgesRequest body) {
    assertCanPunishUser(actorId, targetId);
    if (body == null || (body.liveHost() == null && body.verified() == null && body.official() == null)) {
      throw BusinessException.validation("liveHost、verified 或 official 至少填一项");
    }
    try {
      userClient.setBadges(internalToken, targetId, body.liveHost(), body.verified(), body.official());
    } catch (RuntimeException ex) {
      log.warn("ops set badges {} failed", targetId, ex);
      throw BusinessException.configMissing("更新标识失败，请稍后重试");
    }
    return getUser(targetId);
  }

  public ModerationDtos.OpsPeerPage opsFollowing(long userId, String cursor, Integer limit) {
    return toPeerPage(userClient.following(internalToken, userId, cursor, limit));
  }

  public ModerationDtos.OpsPeerPage opsFollowers(long userId, String cursor, Integer limit) {
    return toPeerPage(userClient.followers(internalToken, userId, cursor, limit));
  }

  public void removeFollow(long followerId, long followeeId) {
    userClient.removeFollow(internalToken, followerId, followeeId);
  }

  public ModerationDtos.OpsNotifyPage opsNotifications(
      long userId, String group, String cursor, Integer limit) {
    NotifyClient.NotifyPage page = notifyClient.page(internalToken, userId, group, cursor, limit);
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsNotifyPage(List.of(), null, false);
    }
    List<ModerationDtos.OpsNotifyRow> items = new ArrayList<>();
    for (NotifyClient.NotifyItem row : page.items()) {
      if (row == null) {
        continue;
      }
      items.add(new ModerationDtos.OpsNotifyRow(
          row.id(),
          row.type() == null ? "" : row.type(),
          row.actor(),
          row.postId() == null ? "" : row.postId(),
          row.commentId() == null ? "" : row.commentId(),
          row.preview() == null ? "" : row.preview(),
          row.read(),
          row.createdAt() == null ? "" : row.createdAt()));
    }
    return new ModerationDtos.OpsNotifyPage(items, page.nextCursor(), page.hasMore());
  }

  public void hideNotification(long id) {
    notifyClient.hide(internalToken, id);
  }

  private static ModerationDtos.OpsPeerPage toPeerPage(UserClient.PeerPage page) {
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsPeerPage(List.of(), null, false);
    }
    List<UserSummary> items = new ArrayList<>();
    for (UserSummary row : page.items()) {
      if (row != null) {
        items.add(row);
      }
    }
    return new ModerationDtos.OpsPeerPage(items, page.nextCursor(), page.hasMore());
  }

  private void applyAction(long handlerId, Report row, String action) {
    if ("dismiss".equals(action)) {
      return;
    }
    if ("takedown".equals(action)) {
      if ("comment".equals(row.getTargetType())) {
        contentClient.hideComment(internalToken, row.getTargetId());
        return;
      }
      if ("live".equals(row.getTargetType())) {
        liveClient.end(internalToken, row.getTargetId());
        return;
      }
      requirePost(row);
      contentClient.takedown(internalToken, row.getTargetId());
      return;
    }
    if ("restore".equals(action)) {
      if ("live".equals(row.getTargetType())) {
        throw BusinessException.validation("直播关播后不能恢复");
      }
      if ("comment".equals(row.getTargetType())) {
        contentClient.restoreComment(internalToken, row.getTargetId());
        return;
      }
      requirePost(row);
      contentClient.restore(internalToken, row.getTargetId());
      return;
    }
    long punishId = punishUserId(row);
    assertCanPunishUser(handlerId, punishId);
    if ("ban".equals(action)) {
      if ("post".equals(row.getTargetType())) {
        contentClient.takedown(internalToken, row.getTargetId());
      } else if ("comment".equals(row.getTargetType())) {
        contentClient.hideComment(internalToken, row.getTargetId());
      } else if ("live".equals(row.getTargetType())) {
        liveClient.end(internalToken, row.getTargetId());
      }
      authClient.freeze(internalToken, punishId);
      return;
    }
    authClient.unfreeze(internalToken, punishId);
  }

  private void requirePost(Report row) {
    if (!"post".equals(row.getTargetType())) {
      throw BusinessException.validation("该动作只能用于笔记");
    }
  }

  private long punishUserId(Report row) {
    if ("user".equals(row.getTargetType())) {
      return row.getTargetId();
    }
    if ("comment".equals(row.getTargetType())) {
      List<ContentClient.OpsCommentRow> list = contentClient.commentBriefs(internalToken, List.of(row.getTargetId()));
      if (list == null || list.isEmpty() || list.get(0).author() == null
          || !StringUtils.hasText(list.get(0).author().userId())) {
        throw BusinessException.notFound("找不到评论作者");
      }
      try {
        return Long.parseLong(list.get(0).author().userId());
      } catch (NumberFormatException ex) {
        throw BusinessException.notFound("找不到评论作者");
      }
    }
    if ("live".equals(row.getTargetType())) {
      LiveClient.LiveItem live = liveClient.session(internalToken, row.getTargetId());
      if (live == null || live.owner() == null || !StringUtils.hasText(live.owner().userId())) {
        throw BusinessException.notFound("找不到主播");
      }
      try {
        return Long.parseLong(live.owner().userId());
      } catch (NumberFormatException ex) {
        throw BusinessException.notFound("找不到主播");
      }
    }
    requirePost(row);
    List<ContentClient.PostBrief> list = contentClient.postBriefs(internalToken, List.of(row.getTargetId()));
    if (list == null || list.isEmpty() || !StringUtils.hasText(list.get(0).authorId())) {
      throw BusinessException.notFound("找不到笔记作者");
    }
    try {
      return Long.parseLong(list.get(0).authorId());
    } catch (NumberFormatException ex) {
      throw BusinessException.notFound("找不到笔记作者");
    }
  }

  private Map<Long, UserSummary> loadPeople(
      List<Report> rows,
      Map<Long, ContentClient.PostBrief> posts,
      Map<Long, ContentClient.OpsCommentRow> comments,
      Map<Long, LiveClient.LiveItem> lives) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Report row : rows) {
      if (row.getReporterId() != null) {
        ids.add(row.getReporterId());
      }
      if (row.getHandlerId() != null) {
        ids.add(row.getHandlerId());
      }
      if ("user".equals(row.getTargetType()) && row.getTargetId() != null) {
        ids.add(row.getTargetId());
      }
    }
    for (ContentClient.PostBrief brief : posts.values()) {
      if (brief != null && StringUtils.hasText(brief.authorId())) {
        try {
          ids.add(Long.parseLong(brief.authorId()));
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
    }
    for (ContentClient.OpsCommentRow comment : comments.values()) {
      if (comment != null && comment.author() != null && StringUtils.hasText(comment.author().userId())) {
        try {
          ids.add(Long.parseLong(comment.author().userId()));
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
    }
    for (LiveClient.LiveItem live : lives.values()) {
      if (live != null && live.owner() != null && StringUtils.hasText(live.owner().userId())) {
        try {
          ids.add(Long.parseLong(live.owner().userId()));
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<UserSummary> list = userClient.briefs(internalToken, List.copyOf(ids));
    if (list == null) {
      return Map.of();
    }
    return list.stream().collect(Collectors.toMap(u -> Long.parseLong(u.userId()), Function.identity(), (a, b) -> a));
  }

  private Map<Long, ContentClient.PostBrief> loadPosts(List<Report> rows) {
    List<Long> ids = new ArrayList<>();
    for (Report row : rows) {
      if ("post".equals(row.getTargetType()) && row.getTargetId() != null) {
        ids.add(row.getTargetId());
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<ContentClient.PostBrief> list = contentClient.postBriefs(internalToken, ids);
    if (list == null) {
      return Map.of();
    }
    Map<Long, ContentClient.PostBrief> map = new java.util.HashMap<>();
    for (ContentClient.PostBrief brief : list) {
      if (brief != null && StringUtils.hasText(brief.postId())) {
        map.put(Long.parseLong(brief.postId()), brief);
      }
    }
    return map;
  }

  private Map<Long, ContentClient.OpsCommentRow> loadComments(List<Report> rows) {
    List<Long> ids = new ArrayList<>();
    for (Report row : rows) {
      if ("comment".equals(row.getTargetType()) && row.getTargetId() != null) {
        ids.add(row.getTargetId());
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<ContentClient.OpsCommentRow> list = contentClient.commentBriefs(internalToken, ids);
    if (list == null) {
      return Map.of();
    }
    Map<Long, ContentClient.OpsCommentRow> map = new java.util.HashMap<>();
    for (ContentClient.OpsCommentRow brief : list) {
      if (brief != null && StringUtils.hasText(brief.commentId())) {
        map.put(Long.parseLong(brief.commentId()), brief);
      }
    }
    return map;
  }

  private Map<Long, LiveClient.LiveItem> loadLives(List<Report> rows) {
    List<Long> ids = new ArrayList<>();
    for (Report row : rows) {
      if ("live".equals(row.getTargetType()) && row.getTargetId() != null) {
        ids.add(row.getTargetId());
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    try {
      List<LiveClient.LiveItem> list = liveClient.briefs(internalToken, ids);
      if (list == null) {
        return Map.of();
      }
      Map<Long, LiveClient.LiveItem> map = new java.util.HashMap<>();
      for (LiveClient.LiveItem brief : list) {
        if (brief != null && StringUtils.hasText(brief.sessionId())) {
          map.put(Long.parseLong(brief.sessionId()), brief);
        }
      }
      return map;
    } catch (RuntimeException ex) {
      log.warn("ops live-briefs failed");
      return Map.of();
    }
  }

  public ModerationDtos.OpsLiveView opsLive(long sessionId) {
    LiveClient.LiveItem raw = liveClient.session(internalToken, sessionId);
    if (raw == null) {
      throw BusinessException.notFound("场次不存在");
    }
    return toLiveView(raw);
  }

  private static ModerationDtos.OpsLiveView toLiveView(LiveClient.LiveItem raw) {
    boolean ended = raw.status() == null || !"live".equals(raw.status());
    return new ModerationDtos.OpsLiveView(
        raw.sessionId() == null ? "" : raw.sessionId(),
        raw.roomId() == null ? "" : raw.roomId(),
        raw.title() == null ? "" : raw.title(),
        raw.status() == null ? "" : raw.status(),
        raw.coverUrl() == null ? "" : raw.coverUrl(),
        raw.notice() == null ? "" : raw.notice(),
        raw.owner(),
        ended);
  }

  private static ModerationDtos.OpsReport toView(
      Report row,
      Map<Long, UserSummary> people,
      Map<Long, ContentClient.PostBrief> posts,
      Map<Long, ContentClient.OpsCommentRow> comments,
      Map<Long, LiveClient.LiveItem> lives) {
    String title = "";
    String pageId = "";
    String authorId = "";
    boolean takenDown = false;
    if ("post".equals(row.getTargetType())) {
      ContentClient.PostBrief brief = posts.get(row.getTargetId());
      if (brief != null) {
        title = brief.title() == null ? "" : brief.title();
        authorId = brief.authorId() == null ? "" : brief.authorId();
        takenDown = brief.takenDown();
        if (StringUtils.hasText(authorId)) {
          try {
            UserSummary author = people.get(Long.parseLong(authorId));
            if (author != null && author.pageId() != null) {
              pageId = author.pageId();
            }
          } catch (NumberFormatException ignored) {
            // skip
          }
        }
      }
    } else if ("user".equals(row.getTargetType())) {
      UserSummary target = people.get(row.getTargetId());
      if (target != null) {
        title = target.nickname() == null ? "" : target.nickname();
        pageId = target.pageId() == null ? "" : target.pageId();
      }
    } else if ("live".equals(row.getTargetType())) {
      LiveClient.LiveItem brief = lives.get(row.getTargetId());
      title = "直播场次";
      if (brief != null) {
        title = StringUtils.hasText(brief.title()) ? brief.title() : title;
        takenDown = brief.status() == null || !"live".equals(brief.status());
        if (brief.owner() != null) {
          authorId = brief.owner().userId() == null ? "" : brief.owner().userId();
          pageId = brief.owner().pageId() == null ? "" : brief.owner().pageId();
        }
      }
    } else if ("comment".equals(row.getTargetType())) {
      ContentClient.OpsCommentRow brief = comments.get(row.getTargetId());
      if (brief != null) {
        String raw = brief.content() == null ? "" : brief.content().trim();
        title = raw.length() > 40 ? raw.substring(0, 40) + "…" : raw;
        takenDown = brief.hidden();
        if (brief.author() != null) {
          authorId = brief.author().userId() == null ? "" : brief.author().userId();
          pageId = brief.author().pageId() == null ? "" : brief.author().pageId();
        }
      }
    }
    return new ModerationDtos.OpsReport(
        String.valueOf(row.getId()),
        row.getTargetType(),
        String.valueOf(row.getTargetId()),
        title,
        pageId,
        authorId,
        takenDown,
        row.getReasonCode(),
        row.getDetail() == null ? "" : row.getDetail(),
        row.getStatus() != null && row.getStatus() == STATUS_DONE ? "done" : "pending",
        people.get(row.getReporterId()),
        people.get(row.getHandlerId()),
        row.getHandleAction() == null ? "" : row.getHandleAction(),
        row.getHandleNote() == null ? "" : row.getHandleNote(),
        iso(row.getCreatedAt()),
        iso(row.getHandledAt()));
  }

  public ModerationDtos.OpsConversationPage opsConversations(String q, String cursor, Integer limit) {
    MessageClient.OpsConversationPage page = messageClient.conversations(internalToken, q, cursor, limit);
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsConversationPage(List.of(), null, false);
    }
    List<ModerationDtos.OpsConversationRow> items = new ArrayList<>();
    for (MessageClient.OpsConversationRow row : page.items()) {
      if (row == null) {
        continue;
      }
      items.add(new ModerationDtos.OpsConversationRow(
          row.conversationId(),
          imPeer(row.left()),
          imPeer(row.right()),
          row.preview() == null ? "" : row.preview(),
          row.updatedAt() == null ? "" : row.updatedAt()));
    }
    return new ModerationDtos.OpsConversationPage(items, page.nextCursor(), page.hasMore());
  }

  public ModerationDtos.OpsImMessagePage opsImMessages(
      long conversationId, String hidden, String cursor, Integer limit) {
    MessageClient.OpsMessagePage page =
        messageClient.messages(internalToken, conversationId, hidden, cursor, limit);
    if (page == null || page.items() == null) {
      return new ModerationDtos.OpsImMessagePage(List.of(), null, false);
    }
    List<ModerationDtos.OpsImMessageRow> items = new ArrayList<>();
    for (MessageClient.OpsMessageRow row : page.items()) {
      if (row == null) {
        continue;
      }
      items.add(new ModerationDtos.OpsImMessageRow(
          row.messageId(),
          row.conversationId(),
          imPeer(row.sender()),
          row.msgType() == null ? "text" : row.msgType(),
          row.preview() == null ? "" : row.preview(),
          row.imageUrl() == null ? "" : row.imageUrl(),
          row.postId() == null ? "" : row.postId(),
          row.hidden(),
          row.createdAt() == null ? "" : row.createdAt()));
    }
    return new ModerationDtos.OpsImMessagePage(items, page.nextCursor(), page.hasMore());
  }

  public void hideImMessage(long messageId) {
    messageClient.hide(internalToken, messageId);
  }

  public void restoreImMessage(long messageId) {
    messageClient.restore(internalToken, messageId);
  }

  private static ModerationDtos.OpsImPeer imPeer(MessageClient.UserBrief row) {
    if (row == null) {
      return new ModerationDtos.OpsImPeer("", "", "用户", "");
    }
    return new ModerationDtos.OpsImPeer(
        row.userId() == null ? "" : row.userId(),
        row.pageId() == null ? "" : row.pageId(),
        row.nickname() == null ? "用户" : row.nickname(),
        row.avatarUrl() == null ? "" : row.avatarUrl());
  }

  private void assertCanPunishUser(long actorId, long targetId) {
    if (targetId == actorId) {
      throw BusinessException.forbidden("不能处置自己");
    }
    if (staffGuard.isStaff(targetId)) {
      throw BusinessException.forbidden("不能处置运营员工");
    }
  }

  private static Integer parseStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return null;
    }
    return switch (status.trim()) {
      case "pending" -> STATUS_PENDING;
      case "done" -> STATUS_DONE;
      default -> throw BusinessException.validation("status 须为 pending 或 done");
    };
  }

  private static long parseId(String raw, String field) {
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      throw BusinessException.validation(field + " 不合法");
    }
  }

  private static String iso(LocalDateTime time) {
    if (time == null) {
      return "";
    }
    return time.atZone(ZoneId.systemDefault()).toInstant().toString();
  }

  private static String blankToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private static long n(Long value) {
    return value == null ? 0L : value;
  }
}
