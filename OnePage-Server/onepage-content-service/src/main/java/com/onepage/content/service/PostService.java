package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.client.ImClient;
import com.onepage.content.client.UserClient;
import com.onepage.content.dto.FeedDtos;
import com.onepage.content.dto.InteractionHit;
import com.onepage.content.dto.ReceivedActorHit;
import com.onepage.content.entity.MediaAsset;
import com.onepage.content.entity.OutboxEvent;
import com.onepage.content.entity.Post;
import com.onepage.content.entity.PostFavorite;
import com.onepage.content.entity.PostLike;
import com.onepage.content.entity.PostMedia;
import com.onepage.content.entity.Comment;
import com.onepage.content.mapper.CommentMapper;
import com.onepage.content.mapper.MediaAssetMapper;
import com.onepage.content.mapper.OutboxEventMapper;
import com.onepage.content.mapper.PostFavoriteMapper;
import com.onepage.content.mapper.PostLikeMapper;
import com.onepage.content.mapper.PostMapper;
import com.onepage.content.mapper.PostMediaMapper;
import com.onepage.content.mapper.PostViewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发帖、详情、软删、浏览。发布与 Outbox 同一事务；MQ 消费留到后续。
 */
@Service
public class PostService {
  private static final Logger log = LoggerFactory.getLogger(PostService.class);
  private static final Set<String> POST_TYPES = Set.of("text", "image", "video", "mixed");
  private static final String STATUS_DRAFT = "draft";
  private static final String STATUS_PENDING = "pending";
  private static final String STATUS_PUBLISHED = "published";
  private static final String STATUS_REJECTED = "rejected";
  private static final String STATUS_DELETED = "deleted";
  private static final DateTimeFormatter SCHEDULE_MIN =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
  private static final Set<String> CATEGORIES =
      Set.of("旅行", "美食", "时尚", "摄影", "生活", "运动", "游戏", "音乐");
  private static final Set<String> CONTENT_KINDS =
      Set.of("", "不声明", "虚构创作", "个人观点", "专业内容");
  private final PostMapper postMapper;
  private final PostMediaMapper postMediaMapper;
  private final MediaAssetMapper mediaAssetMapper;
  private final OutboxEventMapper outboxEventMapper;
  private final PostLikeMapper likeMapper;
  private final PostFavoriteMapper favoriteMapper;
  private final CommentMapper commentMapper;
  private final PostViewMapper postViewMapper;
  private final AuthorLookup authorLookup;
  private final CatalogService catalogService;
  private final UserClient userClient;
  private final InteractionNotifier interactionNotifier;
  private final SearchIndexer searchIndexer;
  private final ObjectMapper objectMapper;
  private final ImClient imClient;
  private final String mediaPublicBase;
  private final String frontendUrl;
  private final String internalToken;

  public PostService(
      PostMapper postMapper,
      PostMediaMapper postMediaMapper,
      MediaAssetMapper mediaAssetMapper,
      OutboxEventMapper outboxEventMapper,
      PostLikeMapper likeMapper,
      PostFavoriteMapper favoriteMapper,
      CommentMapper commentMapper,
      PostViewMapper postViewMapper,
      AuthorLookup authorLookup,
      CatalogService catalogService,
      UserClient userClient,
      InteractionNotifier interactionNotifier,
      SearchIndexer searchIndexer,
      ObjectMapper objectMapper,
      ImClient imClient,
      @Value("${onepage.media-public-base}") String mediaPublicBase,
      @Value("${onepage.frontend-url}") String frontendUrl,
      @Value("${onepage.internal-token}") String internalToken) {
    this.postMapper = postMapper;
    this.postMediaMapper = postMediaMapper;
    this.mediaAssetMapper = mediaAssetMapper;
    this.outboxEventMapper = outboxEventMapper;
    this.likeMapper = likeMapper;
    this.favoriteMapper = favoriteMapper;
    this.commentMapper = commentMapper;
    this.postViewMapper = postViewMapper;
    this.authorLookup = authorLookup;
    this.catalogService = catalogService;
    this.userClient = userClient;
    this.interactionNotifier = interactionNotifier;
    this.searchIndexer = searchIndexer;
    this.objectMapper = objectMapper;
    this.imClient = imClient;
    this.mediaPublicBase = trimSlash(mediaPublicBase);
    this.frontendUrl = trimSlash(frontendUrl);
    this.internalToken = internalToken;
  }

  @Transactional
  public FeedDtos.CreateResult publish(long userId, FeedDtos.PublishSpec spec) {
    boolean draft = Boolean.TRUE.equals(spec.draft());
    String type = spec.postType() == null ? "" : spec.postType().trim().toLowerCase();
    String cat = spec.category() == null ? "" : spec.category().trim();
    List<MediaAsset> assets = loadOwnedReadyMedia(userId, spec.mediaIds());
    validatePublish(spec, type, cat, assets, draft);
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime publishedAt = resolvePublishedAt(spec.scheduledAt(), now, !draft);
    long postId = IdGenerator.nextId();
    Post post = new Post();
    post.setId(postId);
    post.setAuthorId(userId);
    post.setTitle(spec.title() == null ? "" : spec.title().trim());
    post.setBody(spec.body() == null ? "" : spec.body().trim());
    post.setPostType(type);
    post.setCategory(cat);
    post.setOriginal(Boolean.TRUE.equals(spec.original()) ? 1 : 0);
    post.setContentKind(normalizeKind(spec.contentKind()));
    applyCampaign(post, spec.eventId());
    int vis = parseVisibility(spec.visibility());
    post.setVisibility(vis);
    post.setRejectReason(null);
    if (draft) {
      post.setStatus(STATUS_DRAFT);
    } else {
      post.setStatus(vis == 0 ? STATUS_PENDING : STATUS_PUBLISHED);
    }
    post.setPublishedAt(publishedAt);
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    postMapper.insert(post);
    bindMedia(postId, assets);
    if (!draft && vis != 0) {
      writeOutbox("post", String.valueOf(postId), "post.published", Map.of(
          "postId", String.valueOf(postId),
          "authorId", String.valueOf(userId),
          "postType", type));
    }
    catalogService.rememberHashtags(post.getBody());
    return new FeedDtos.CreateResult(String.valueOf(postId), publishedAt.toString(), post.getStatus());
  }

  @Transactional
  public FeedDtos.PostDetail update(long userId, long postId, FeedDtos.PublishSpec spec) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null || !authorEditable(post.getStatus())) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (post.getAuthorId() == null || post.getAuthorId() != userId) {
      throw BusinessException.forbidden("只能修改自己的笔记");
    }
    boolean keepDraft = STATUS_DRAFT.equals(post.getStatus()) && Boolean.TRUE.equals(spec.draft());
    String type = spec.postType() == null ? "" : spec.postType().trim().toLowerCase();
    String cat = spec.category() == null ? "" : spec.category().trim();
    List<MediaAsset> assets = loadOwnedReadyMedia(userId, spec.mediaIds());
    validatePublish(spec, type, cat, assets, keepDraft);
    LocalDateTime now = LocalDateTime.now();
    post.setTitle(spec.title() == null ? "" : spec.title().trim());
    post.setBody(spec.body() == null ? "" : spec.body().trim());
    post.setPostType(type);
    post.setCategory(cat);
    post.setOriginal(Boolean.TRUE.equals(spec.original()) ? 1 : 0);
    post.setContentKind(normalizeKind(spec.contentKind()));
    applyCampaign(post, spec.eventId());
    int vis = parseVisibility(spec.visibility());
    post.setVisibility(vis);
    if (keepDraft) {
      post.setStatus(STATUS_DRAFT);
      if (StringUtils.hasText(spec.scheduledAt())) {
        post.setPublishedAt(resolvePublishedAt(spec.scheduledAt(), now, false));
      }
    } else if (vis != 0 && (STATUS_PENDING.equals(post.getStatus())
        || STATUS_REJECTED.equals(post.getStatus())
        || STATUS_DRAFT.equals(post.getStatus()))) {
      post.setStatus(STATUS_PUBLISHED);
      post.setRejectReason(null);
      post.setPublishedAt(resolvePublishedAt(spec.scheduledAt(), now, true));
    } else if (STATUS_REJECTED.equals(post.getStatus())
        || STATUS_DRAFT.equals(post.getStatus())
        || (vis == 0 && !STATUS_PUBLISHED.equals(post.getStatus()))) {
      post.setStatus(STATUS_PENDING);
      post.setRejectReason(null);
      post.setPublishedAt(resolvePublishedAt(spec.scheduledAt(), now, true));
    } else if (StringUtils.hasText(spec.scheduledAt())) {
      post.setPublishedAt(resolvePublishedAt(spec.scheduledAt(), now, true));
    }
    post.setUpdatedAt(now);
    postMapper.updateById(post);
    postMediaMapper.delete(Wrappers.<PostMedia>lambdaQuery().eq(PostMedia::getPostId, postId));
    bindMedia(postId, assets);
    writeOutbox("post", String.valueOf(postId), "post.updated", Map.of(
        "postId", String.valueOf(postId),
        "authorId", String.valueOf(userId)));
    catalogService.rememberHashtags(post.getBody());
    return detail(userId, postId);
  }

  public FeedDtos.PostDetail detail(Long viewerId, long postId) {
    Post post = requireVisible(postId, viewerId);
    List<PostMedia> links = postMediaMapper.selectList(
        Wrappers.<PostMedia>lambdaQuery().eq(PostMedia::getPostId, postId).orderByAsc(PostMedia::getSortOrder));
    List<FeedDtos.MediaItem> media = new ArrayList<>();
    for (PostMedia link : links) {
      MediaAsset asset = mediaAssetMapper.selectById(link.getMediaId());
      if (asset == null || asset.getBlockedAt() != null) {
        continue;
      }
      media.add(new FeedDtos.MediaItem(
          String.valueOf(asset.getId()),
          publicUrl(asset.getObjectKey()),
          asset.getMimeType(),
          link.getRole()));
    }
    long likes = likeMapper.selectCount(Wrappers.<PostLike>lambdaQuery().eq(PostLike::getPostId, postId));
    long favs = favoriteMapper.selectCount(Wrappers.<PostFavorite>lambdaQuery().eq(PostFavorite::getPostId, postId));
    long comments = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
        .eq(Comment::getPostId, postId)
        .eq(Comment::getStatus, 0));
    boolean liked = viewerId != null && likeMapper.selectCount(Wrappers.<PostLike>lambdaQuery()
        .eq(PostLike::getPostId, postId).eq(PostLike::getUserId, viewerId)) > 0;
    boolean favorited = viewerId != null && favoriteMapper.selectCount(Wrappers.<PostFavorite>lambdaQuery()
        .eq(PostFavorite::getPostId, postId).eq(PostFavorite::getUserId, viewerId)) > 0;
    String cover = media.isEmpty() ? "" : media.getFirst().url();
    return new FeedDtos.PostDetail(
        String.valueOf(post.getId()),
        authorLookup.brief(post.getAuthorId()),
        post.getTitle() == null ? "" : post.getTitle(),
        post.getBody(),
        cover,
        post.getPostType(),
        post.getCategory() == null ? "" : post.getCategory(),
        originalOf(post),
        blankToEmpty(post.getContentKind()),
        blankToEmpty(post.getEventId()),
        blankToEmpty(post.getEventTitle()),
        visLabel(post.getVisibility()),
        media,
        likes,
        favs,
        comments,
        liked,
        favorited,
        iso(post.getPublishedAt()),
        reviewStatusOf(post),
        blankToEmpty(post.getRejectReason()));
  }

  @Transactional
  public void softDelete(long userId, long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (post.getAuthorId() == null || post.getAuthorId() != userId) {
      throw BusinessException.forbidden("只能删除自己的笔记");
    }
    LocalDateTime now = LocalDateTime.now();
    post.setStatus(STATUS_DELETED);
    post.setDeletedAt(now);
    post.setUpdatedAt(now);
    postMapper.updateById(post);
    writeOutbox("post", String.valueOf(postId), "post.deleted", Map.of("postId", String.valueOf(postId)));
  }

  /** 运营工单展示标题；已删也返回。找不到的 id 跳过。 */
  public List<FeedDtos.OpsPostBrief> opsBriefs(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<Long> uniq = new LinkedHashSet<>();
    for (Long id : ids) {
      if (id != null) {
        uniq.add(id);
      }
      if (uniq.size() >= 50) {
        break;
      }
    }
    List<FeedDtos.OpsPostBrief> out = new ArrayList<>();
    for (Long id : uniq) {
      Post post = postMapper.selectById(id);
      if (post == null) {
        continue;
      }
      String title = post.getTitle() == null ? "" : post.getTitle();
      String authorId = post.getAuthorId() == null ? "" : String.valueOf(post.getAuthorId());
      out.add(new FeedDtos.OpsPostBrief(String.valueOf(id), title, authorId, post.getDeletedAt() != null));
    }
    return out;
  }

  /** 运营预览：不校验可见性，已下架也返回。 */
  public FeedDtos.OpsPostView opsDetail(long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null) {
      throw BusinessException.notFound("笔记不存在");
    }
    List<PostMedia> links = postMediaMapper.selectList(
        Wrappers.<PostMedia>lambdaQuery().eq(PostMedia::getPostId, postId).orderByAsc(PostMedia::getSortOrder));
    List<FeedDtos.MediaItem> media = new ArrayList<>();
    for (PostMedia link : links) {
      MediaAsset asset = mediaAssetMapper.selectById(link.getMediaId());
      if (asset == null) {
        continue;
      }
      media.add(new FeedDtos.MediaItem(
          String.valueOf(asset.getId()),
          publicUrl(asset.getObjectKey()),
          asset.getMimeType(),
          link.getRole()));
    }
    String cover = media.isEmpty() ? "" : media.getFirst().url();
    return new FeedDtos.OpsPostView(
        String.valueOf(post.getId()),
        authorLookup.brief(post.getAuthorId()),
        post.getTitle() == null ? "" : post.getTitle(),
        post.getBody() == null ? "" : post.getBody(),
        cover,
        post.getPostType(),
        post.getCategory() == null ? "" : post.getCategory(),
        originalOf(post),
        blankToEmpty(post.getContentKind()),
        blankToEmpty(post.getEventTitle()),
        visLabel(post.getVisibility()),
        media,
        post.getDeletedAt() != null,
        iso(post.getPublishedAt()),
        reviewStatusOf(post),
        blankToEmpty(post.getRejectReason()));
  }

  /** 运营下架：不校验作者，已删则当作成功。 */
  @Transactional
  public void opsTakedown(long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (post.getDeletedAt() != null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    post.setStatus(STATUS_DELETED);
    post.setDeletedAt(now);
    post.setUpdatedAt(now);
    postMapper.updateById(post);
    writeOutbox("post", String.valueOf(postId), "post.deleted", Map.of("postId", String.valueOf(postId)));
  }

  /** 运营恢复：已在线则当作成功。 */
  @Transactional
  public void opsRestore(long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (post.getDeletedAt() == null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    LambdaUpdateWrapper<Post> update = Wrappers.lambdaUpdate();
    update.eq(Post::getId, postId)
        .set(Post::getStatus, STATUS_PUBLISHED)
        .set(Post::getRejectReason, null)
        .set(Post::getDeletedAt, null)
        .set(Post::getUpdatedAt, now);
    postMapper.update(null, update);
    writeOutbox("post", String.valueOf(postId), "post.restored", Map.of("postId", String.valueOf(postId)));
  }

  /** 运营通过待审或未通过笔记，写入 Feed。 */
  @Transactional
  public void opsApprove(long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (STATUS_PUBLISHED.equals(post.getStatus())) {
      return;
    }
    if (!STATUS_PENDING.equals(post.getStatus()) && !STATUS_REJECTED.equals(post.getStatus())) {
      throw BusinessException.validation("当前状态不能通过");
    }
    LocalDateTime now = LocalDateTime.now();
    LambdaUpdateWrapper<Post> update = Wrappers.lambdaUpdate();
    update.eq(Post::getId, postId)
        .set(Post::getStatus, STATUS_PUBLISHED)
        .set(Post::getRejectReason, null)
        .set(Post::getUpdatedAt, now);
    if (post.getPublishedAt() == null) {
      update.set(Post::getPublishedAt, now);
    }
    postMapper.update(null, update);
    writeOutbox("post", String.valueOf(postId), "post.published", Map.of(
        "postId", String.valueOf(postId),
        "authorId", post.getAuthorId() == null ? "" : String.valueOf(post.getAuthorId()),
        "postType", post.getPostType() == null ? "" : post.getPostType()));
  }

  /** 运营驳回待审。必须写回复，最长 200；事务提交后通知作者。 */
  @Transactional
  public void opsReject(long postId, String reason, Long handlerId) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (!STATUS_PENDING.equals(post.getStatus()) && !STATUS_REJECTED.equals(post.getStatus())) {
      throw BusinessException.validation("只能驳回待审笔记");
    }
    String note = reason == null ? "" : reason.trim();
    if (!StringUtils.hasText(note)) {
      throw BusinessException.validation("请填写回复信息");
    }
    if (note.length() > 200) {
      throw BusinessException.validation("未通过原因过长");
    }
    LocalDateTime now = LocalDateTime.now();
    post.setStatus(STATUS_REJECTED);
    post.setRejectReason(note);
    post.setUpdatedAt(now);
    postMapper.updateById(post);
    if (handlerId != null && post.getAuthorId() != null) {
      interactionNotifier.emit(
          post.getAuthorId(),
          "review_reject",
          handlerId,
          "review_reject:" + postId,
          postId,
          null,
          note);
    }
    searchIndexer.sync(postId);
  }

  /** 运营笔记列表。taken=live 已通过在线，pending 待审，rejected 未通过，down 已下架。 */
  public FeedDtos.OpsPostPage opsPage(String q, String taken, String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LambdaQueryWrapper<Post> query = Wrappers.<Post>lambdaQuery();
    if ("down".equals(taken)) {
      query.isNotNull(Post::getDeletedAt);
    } else if ("pending".equals(taken)) {
      query.isNull(Post::getDeletedAt).eq(Post::getStatus, STATUS_PENDING);
    } else if ("rejected".equals(taken)) {
      query.isNull(Post::getDeletedAt).eq(Post::getStatus, STATUS_REJECTED);
    } else if ("live".equals(taken)) {
      query.isNull(Post::getDeletedAt).eq(Post::getStatus, STATUS_PUBLISHED);
    }
    if (StringUtils.hasText(q)) {
      String text = q.trim();
      query.and(w -> {
        w.like(Post::getTitle, text);
        try {
          w.or().eq(Post::getId, Long.parseLong(text));
        } catch (NumberFormatException ignored) {
          // 只按标题
        }
      });
    }
    applyCursor(query, cursor);
    query.orderByDesc(Post::getPublishedAt).orderByDesc(Post::getId).last("LIMIT " + (size + 1));
    List<Post> rows = postMapper.selectList(query);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    List<FeedDtos.OpsPostRow> items = new ArrayList<>();
    for (Post post : rows) {
      items.add(new FeedDtos.OpsPostRow(
          String.valueOf(post.getId()),
          authorLookup.brief(post.getAuthorId()),
          post.getTitle() == null ? "" : post.getTitle(),
          post.getPostType() == null ? "" : post.getPostType(),
          post.getCategory() == null ? "" : post.getCategory(),
          post.getDeletedAt() != null,
          iso(post.getPublishedAt()),
          reviewStatusOf(post),
          blankToEmpty(post.getRejectReason())));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      Post last = rows.getLast();
      if (last.getPublishedAt() != null && last.getId() != null) {
        next = epochMilli(last.getPublishedAt()) + "_" + last.getId();
      }
    }
    return new FeedDtos.OpsPostPage(items, next, hasMore);
  }

  public FeedDtos.OpsCatalog opsCatalog() {
    return catalogService.opsCatalog();
  }

  public FeedDtos.CursorPage listMine(long userId, String cursor, Integer limit) {
    return listByAuthor(userId, userId, cursor, limit);
  }

  /** 本人收藏，按收藏时间倒序。已删或他人仅自己可见的帖不出现。 */
  public FeedDtos.CursorPage listFavorites(long userId, String cursor, Integer limit) {
    return listByInteraction(userId, cursor, limit, InteractionKind.FAVORITE);
  }

  /** 本人点赞，按点赞时间倒序。 */
  public FeedDtos.CursorPage listLikes(long userId, String cursor, Integer limit) {
    return listByInteraction(userId, cursor, limit, InteractionKind.LIKE);
  }

  /** 本人浏览，按最近浏览时间倒序。 */
  public FeedDtos.CursorPage listHistory(long userId, String cursor, Integer limit) {
    return listByInteraction(userId, cursor, limit, InteractionKind.HISTORY);
  }

  public List<String> listHistoryIds(long userId) {
    List<Long> ids = postViewMapper.listPostIds(userId);
    List<String> out = new ArrayList<>();
    for (Long id : ids) {
      if (id != null) {
        out.add(String.valueOf(id));
      }
    }
    return out;
  }

  public void deleteHistory(long userId, long postId) {
    postViewMapper.deleteOne(userId, postId);
  }

  public void clearHistory(long userId) {
    postViewMapper.deleteAll(userId);
  }

  /** 别人给自己笔记点的赞，按时间倒序。 */
  public FeedDtos.ReceivedPage listLikesReceived(long authorId, String cursor, Integer limit) {
    return listReceived(authorId, cursor, limit, true);
  }

  /** 别人收藏自己笔记，按时间倒序。 */
  public FeedDtos.ReceivedPage listFavoritesReceived(long authorId, String cursor, Integer limit) {
    return listReceived(authorId, cursor, limit, false);
  }

  public FeedDtos.CursorPage listByAuthor(long authorId, Long viewerId, String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    boolean self = viewerId != null && viewerId == authorId;
    LambdaQueryWrapper<Post> query = Wrappers.<Post>lambdaQuery()
        .eq(Post::getAuthorId, authorId)
        .isNull(Post::getDeletedAt);
    if (self) {
      query.in(Post::getStatus, List.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_PENDING, STATUS_REJECTED));
    } else {
      query.eq(Post::getStatus, STATUS_PUBLISHED)
          .le(Post::getPublishedAt, LocalDateTime.now());
      if (viewerId != null && follows(viewerId, authorId)) {
        query.in(Post::getVisibility, List.of(0, 2));
      } else {
        query.eq(Post::getVisibility, 0);
      }
    }
    applyCursor(query, cursor);
    query.orderByDesc(Post::getPublishedAt)
        .orderByDesc(Post::getId)
        .last("LIMIT " + (size + 1));
    List<Post> rows = postMapper.selectList(query);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    List<FeedDtos.PostBrief> items = new ArrayList<>();
    for (Post post : rows) {
      items.add(toBrief(post, postViewMapper.countByPostId(post.getId())));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      Post last = rows.getLast();
      next = epochMilli(last.getPublishedAt()) + "_" + last.getId();
    }
    return new FeedDtos.CursorPage(items, next, hasMore);
  }

  @Transactional
  public FeedDtos.PostBrief setVisibility(long userId, long postId, String visibility) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (post.getAuthorId() == null || post.getAuthorId() != userId) {
      throw BusinessException.forbidden("只能修改自己的笔记");
    }
    if (!authorEditable(post.getStatus())) {
      throw BusinessException.notFound("笔记不存在");
    }
    int vis = parseVisibility(visibility);
    int old = post.getVisibility() == null ? 0 : post.getVisibility();
    post.setVisibility(vis);
    if (vis == 0 && old != 0 && STATUS_PUBLISHED.equals(post.getStatus())) {
      post.setStatus(STATUS_PENDING);
    } else if (vis == 0 && STATUS_REJECTED.equals(post.getStatus())) {
      post.setStatus(STATUS_PENDING);
      post.setRejectReason(null);
    } else if (vis != 0 && (STATUS_PENDING.equals(post.getStatus()) || STATUS_REJECTED.equals(post.getStatus()))) {
      post.setStatus(STATUS_PUBLISHED);
      post.setRejectReason(null);
    }
    post.setUpdatedAt(LocalDateTime.now());
    postMapper.updateById(post);
    searchIndexer.sync(postId);
    return toBrief(post, postViewMapper.countByPostId(post.getId()));
  }

  public void recordView(long userId, long postId) {
    requireVisible(postId, userId);
    postViewMapper.upsert(userId, postId, LocalDateTime.now());
  }

  public String shareLink(long postId, Long viewerId) {
    requireVisible(postId, viewerId);
    return frontendUrl + "/?post=" + postId;
  }

  public String shareIm(long userId, long postId, String conversationId, String targetUserId) {
    Post post = requireVisible(postId, userId);
    FeedDtos.UserBrief author = authorLookup.brief(post.getAuthorId());
    String title = blankToEmpty(post.getTitle()).isBlank() ? excerpt(post.getBody()) : post.getTitle().trim();
    Map<String, String> card = new LinkedHashMap<>();
    card.put("postId", String.valueOf(post.getId()));
    card.put("title", title);
    card.put("coverUrl", blankToEmpty(firstCoverUrl(post.getId())));
    card.put("authorNickname", author == null || author.nickname() == null ? "" : author.nickname());
    try {
      ImClient.ShareResult result = imClient.shareCard(
          internalToken,
          String.valueOf(userId),
          new ImClient.ShareCardRequest(conversationId, targetUserId, card));
      if (result == null || !StringUtils.hasText(result.messageId())) {
        throw BusinessException.validation("私信发送失败");
      }
      return result.messageId();
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.warn("share im failed postId={} userId={}", postId, userId, ex);
      throw BusinessException.validation("私信发送失败");
    }
  }

  public Post requirePublished(long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null || !STATUS_PUBLISHED.equals(post.getStatus())) {
      throw BusinessException.notFound("笔记不存在");
    }
    return post;
  }

  public Post requireVisible(long postId, Long viewerId) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null) {
      throw BusinessException.notFound("笔记不存在");
    }
    boolean author = viewerId != null && viewerId.equals(post.getAuthorId());
    String status = post.getStatus();
    if (STATUS_DRAFT.equals(status) || STATUS_PENDING.equals(status) || STATUS_REJECTED.equals(status)) {
      if (!author) {
        throw BusinessException.notFound("笔记不存在");
      }
      return post;
    }
    if (!STATUS_PUBLISHED.equals(status)) {
      throw BusinessException.notFound("笔记不存在");
    }
    int vis = post.getVisibility() == null ? 0 : post.getVisibility();
    if (vis == 1 && !author) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (vis == 2 && !author && !follows(viewerId, post.getAuthorId())) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (post.getPublishedAt() != null && post.getPublishedAt().isAfter(LocalDateTime.now()) && !author) {
      throw BusinessException.notFound("笔记不存在");
    }
    return post;
  }

  /** 给 search-service 回填：仅公开、已发布、已到点的笔记。cursor 为上一页最后 postId。 */
  public FeedDtos.CursorPage listSearchable(String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    Long afterId = null;
    if (StringUtils.hasText(cursor)) {
      try {
        afterId = Long.parseLong(cursor.trim());
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("cursor 不合法");
      }
    }
    LocalDateTime now = LocalDateTime.now();
    LambdaQueryWrapper<Post> query = Wrappers.<Post>lambdaQuery()
        .isNull(Post::getDeletedAt)
        .eq(Post::getStatus, STATUS_PUBLISHED)
        .eq(Post::getVisibility, 0)
        .le(Post::getPublishedAt, now)
        .orderByAsc(Post::getId);
    if (afterId != null) {
      query.gt(Post::getId, afterId);
    }
    query.last("LIMIT " + (size + 1));
    List<Post> rows = postMapper.selectList(query);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    List<FeedDtos.PostBrief> items = new ArrayList<>();
    for (Post post : rows) {
      items.add(toBrief(post, postViewMapper.countByPostId(post.getId())));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      next = String.valueOf(rows.getLast().getId());
    }
    return new FeedDtos.CursorPage(items, next, hasMore);
  }

  public FeedDtos.PostBrief toBrief(Post post) {
    return toBrief(post, 0L);
  }

  public FeedDtos.PostBrief toBrief(Post post, long viewCount) {
    String cover = firstCoverUrl(post.getId());
    long likes = likeMapper.selectCount(Wrappers.<PostLike>lambdaQuery().eq(PostLike::getPostId, post.getId()));
    long favs = favoriteMapper.selectCount(Wrappers.<PostFavorite>lambdaQuery().eq(PostFavorite::getPostId, post.getId()));
    long comments = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
        .eq(Comment::getPostId, post.getId())
        .eq(Comment::getStatus, 0));
    return new FeedDtos.PostBrief(
        String.valueOf(post.getId()),
        authorLookup.brief(post.getAuthorId()),
        post.getTitle() == null ? "" : post.getTitle(),
        excerpt(post.getBody()),
        cover,
        post.getPostType(),
        post.getCategory() == null ? "" : post.getCategory(),
        originalOf(post),
        blankToEmpty(post.getContentKind()),
        blankToEmpty(post.getEventId()),
        blankToEmpty(post.getEventTitle()),
        visLabel(post.getVisibility()),
        likes,
        favs,
        comments,
        viewCount,
        iso(post.getPublishedAt()),
        reviewStatusOf(post),
        blankToEmpty(post.getRejectReason()));
  }

  private enum InteractionKind {
    FAVORITE,
    LIKE,
    HISTORY
  }

  private FeedDtos.CursorPage listByInteraction(
      long userId, String cursor, Integer limit, InteractionKind kind) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LocalDateTime cursorAt = null;
    Long cursorPostId = null;
    if (StringUtils.hasText(cursor)) {
      long[] parsed = parseCursor(cursor);
      cursorAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(parsed[0]), ZoneId.systemDefault());
      cursorPostId = parsed[1];
    }
    List<InteractionHit> hits = switch (kind) {
      case FAVORITE -> favoriteMapper.pageByUser(userId, cursorAt, cursorPostId, size + 1);
      case LIKE -> likeMapper.pageByUser(userId, cursorAt, cursorPostId, size + 1);
      case HISTORY -> postViewMapper.pageByUser(userId, cursorAt, cursorPostId, size + 1);
    };
    boolean hasMore = hits.size() > size;
    if (hasMore) {
      hits = hits.subList(0, size);
    }
    List<FeedDtos.PostBrief> items = new ArrayList<>();
    for (InteractionHit hit : hits) {
      if (hit.getPostId() == null) {
        continue;
      }
      Post post = postMapper.selectById(hit.getPostId());
      if (post == null) {
        continue;
      }
      items.add(toBrief(post, postViewMapper.countByPostId(post.getId())));
    }
    String next = null;
    if (hasMore && !hits.isEmpty()) {
      InteractionHit last = hits.getLast();
      if (last.getAt() != null && last.getPostId() != null) {
        next = epochMilli(last.getAt()) + "_" + last.getPostId();
      }
    }
    return new FeedDtos.CursorPage(items, next, hasMore);
  }

  private void applyCursor(LambdaQueryWrapper<Post> query, String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return;
    }
    long[] parsed = parseCursor(cursor);
    LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(parsed[0]), ZoneId.systemDefault());
    long id = parsed[1];
    query.and(w -> w.lt(Post::getPublishedAt, at)
        .or(inner -> inner.eq(Post::getPublishedAt, at).lt(Post::getId, id)));
  }

  private FeedDtos.ReceivedPage listReceived(long authorId, String cursor, Integer limit, boolean likes) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LocalDateTime cursorAt = null;
    Long cursorPostId = null;
    Long cursorUserId = null;
    if (StringUtils.hasText(cursor)) {
      long[] parsed = parseReceivedCursor(cursor);
      cursorAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(parsed[0]), ZoneId.systemDefault());
      cursorPostId = parsed[1];
      cursorUserId = parsed[2];
    }
    List<ReceivedActorHit> hits = likes
        ? likeMapper.pageReceived(authorId, cursorAt, cursorPostId, cursorUserId, size + 1)
        : favoriteMapper.pageReceived(authorId, cursorAt, cursorPostId, cursorUserId, size + 1);
    if (hits == null) {
      hits = List.of();
    }
    boolean hasMore = hits.size() > size;
    if (hasMore) {
      hits = hits.subList(0, size);
    }
    List<FeedDtos.ReceivedHit> items = new ArrayList<>();
    for (ReceivedActorHit hit : hits) {
      if (hit.getUserId() == null || hit.getPostId() == null) {
        continue;
      }
      items.add(new FeedDtos.ReceivedHit(
          String.valueOf(hit.getUserId()),
          String.valueOf(hit.getPostId()),
          blankToEmpty(hit.getTitle()),
          iso(hit.getAt())));
    }
    String next = null;
    if (hasMore && !hits.isEmpty()) {
      ReceivedActorHit last = hits.getLast();
      if (last.getAt() != null && last.getPostId() != null && last.getUserId() != null) {
        next = epochMilli(last.getAt()) + "_" + last.getPostId() + "_" + last.getUserId();
      }
    }
    return new FeedDtos.ReceivedPage(items, next, hasMore);
  }

  private static long[] parseCursor(String cursor) {
    String[] parts = cursor.split("_", 2);
    if (parts.length != 2) {
      throw BusinessException.validation("cursor 不合法");
    }
    try {
      return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])};
    } catch (NumberFormatException ex) {
      throw BusinessException.validation("cursor 不合法");
    }
  }

  private static long[] parseReceivedCursor(String cursor) {
    String[] parts = cursor.split("_", 3);
    if (parts.length != 3) {
      throw BusinessException.validation("cursor 不合法");
    }
    try {
      return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
    } catch (NumberFormatException ex) {
      throw BusinessException.validation("cursor 不合法");
    }
  }

  private static boolean originalOf(Post post) {
    return Integer.valueOf(1).equals(post.getOriginal());
  }

  private static String visLabel(Integer visibility) {
    if (visibility != null && visibility == 1) {
      return "self";
    }
    if (visibility != null && visibility == 2) {
      return "friend";
    }
    return "public";
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String normalizeKind(String raw) {
    String kind = raw == null ? "" : raw.trim();
    if ("不声明".equals(kind)) {
      return "";
    }
    if (!CONTENT_KINDS.contains(kind)) {
      throw BusinessException.validation("内容类型不支持");
    }
    return kind;
  }

  private static int parseVisibility(String raw) {
    if (raw == null || raw.isBlank() || "public".equals(raw)) {
      return 0;
    }
    if ("self".equals(raw)) {
      return 1;
    }
    if ("friend".equals(raw)) {
      return 2;
    }
    throw BusinessException.validation("可见范围不支持");
  }

  private void applyCampaign(Post post, String eventId) {
    if (!StringUtils.hasText(eventId)) {
      post.setEventId("");
      post.setEventTitle("");
      return;
    }
    String id = eventId.trim();
    String title = catalogService.campaignTitle(id);
    if (!StringUtils.hasText(title)) {
      throw BusinessException.validation("活动不存在或未开始");
    }
    post.setEventId(id);
    post.setEventTitle(title);
  }

  private String firstCoverUrl(long postId) {
    List<PostMedia> links = postMediaMapper.selectList(
        Wrappers.<PostMedia>lambdaQuery()
            .eq(PostMedia::getPostId, postId)
            .orderByAsc(PostMedia::getSortOrder));
    String fallback = "";
    for (PostMedia link : links) {
      MediaAsset asset = mediaAssetMapper.selectById(link.getMediaId());
      if (asset == null || asset.getBlockedAt() != null) {
        continue;
      }
      String url = publicUrl(asset.getObjectKey());
      String mime = asset.getMimeType() == null ? "" : asset.getMimeType();
      if (mime.startsWith("image/")) {
        return url;
      }
      if (fallback.isEmpty()) {
        fallback = url;
      }
    }
    return fallback;
  }

  private static String excerpt(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    String trimmed = body.trim();
    return trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed;
  }

  public static String iso(LocalDateTime time) {
    return time == null ? "" : time.toString();
  }

  public static long epochMilli(LocalDateTime time) {
    return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  private List<MediaAsset> loadOwnedReadyMedia(long userId, List<String> mediaIds) {
    if (mediaIds == null || mediaIds.isEmpty()) {
      return List.of();
    }
    List<MediaAsset> assets = new ArrayList<>();
    for (String raw : mediaIds) {
      long mediaId;
      try {
        mediaId = Long.parseLong(raw);
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("mediaId 不合法");
      }
      MediaAsset asset = mediaAssetMapper.selectById(mediaId);
      if (asset == null
          || asset.getOwnerId() == null
          || asset.getOwnerId() != userId
          || asset.getStatus() == null
          || asset.getStatus() != 1
          || asset.getBlockedAt() != null) {
        throw BusinessException.validation("媒体不可用");
      }
      assets.add(asset);
    }
    return assets;
  }

  private String roleFor(MediaAsset asset, int index) {
    String mime = asset.getMimeType() == null ? "" : asset.getMimeType();
    if (mime.startsWith("video/")) {
      return "video";
    }
    return index == 0 ? "cover" : "gallery";
  }

  private void writeOutbox(String aggregateType, String aggregateId, String eventType, Map<String, String> payload) {
    OutboxEvent event = new OutboxEvent();
    event.setId(IdGenerator.nextId());
    event.setAggregateType(aggregateType);
    event.setAggregateId(aggregateId);
    event.setEventType(eventType);
    try {
      event.setPayloadJson(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("outbox 序列化失败", ex);
    }
    event.setStatus(0);
    event.setCreatedAt(LocalDateTime.now());
    outboxEventMapper.insert(event);
    if ("post".equals(aggregateType) && StringUtils.hasText(aggregateId)) {
      try {
        searchIndexer.sync(Long.parseLong(aggregateId));
      } catch (NumberFormatException ignore) {
        // 非数字聚合 id 不进搜索
      }
    }
  }

  private String publicUrl(String objectKey) {
    if (!StringUtils.hasText(objectKey)) {
      return "";
    }
    return mediaPublicBase + "/" + objectKey;
  }

  private static String trimSlash(String value) {
    if (value == null) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static boolean authorEditable(String status) {
    return STATUS_DRAFT.equals(status)
        || STATUS_PUBLISHED.equals(status)
        || STATUS_PENDING.equals(status)
        || STATUS_REJECTED.equals(status);
  }

  private static String reviewStatusOf(Post post) {
    if (post == null || !StringUtils.hasText(post.getStatus()) || STATUS_DELETED.equals(post.getStatus())) {
      return STATUS_PUBLISHED;
    }
    if (STATUS_PENDING.equals(post.getStatus()) || STATUS_REJECTED.equals(post.getStatus())
        || STATUS_PUBLISHED.equals(post.getStatus()) || STATUS_DRAFT.equals(post.getStatus())) {
      return post.getStatus();
    }
    return STATUS_PUBLISHED;
  }

  private void validatePublish(
      FeedDtos.PublishSpec spec, String type, String cat, List<MediaAsset> assets, boolean draft) {
    if (!POST_TYPES.contains(type)) {
      throw BusinessException.validation("postType 不支持");
    }
    if ("text".equals(type) && !assets.isEmpty()) {
      throw BusinessException.validation("纯文本笔记不能带媒体");
    }
    if (draft) {
      boolean empty = !StringUtils.hasText(spec.title())
          && !StringUtils.hasText(spec.body())
          && assets.isEmpty();
      if (empty) {
        throw BusinessException.validation("请先写点内容再暂存");
      }
      if (StringUtils.hasText(cat) && !catalogService.isEnabledCategory(cat)) {
        throw BusinessException.validation("请选择笔记分类");
      }
      return;
    }
    if (!StringUtils.hasText(spec.body())) {
      throw BusinessException.validation("正文不能为空");
    }
    if (!catalogService.isEnabledCategory(cat)) {
      throw BusinessException.validation("请选择笔记分类");
    }
    if (!"text".equals(type) && assets.isEmpty()) {
      throw BusinessException.validation("该类型需要媒体");
    }
  }

  private LocalDateTime resolvePublishedAt(String scheduledAt, LocalDateTime now, boolean requireFuture) {
    if (!StringUtils.hasText(scheduledAt)) {
      return now;
    }
    LocalDateTime at = parseSchedule(scheduledAt);
    if (at.isAfter(now)) {
      return at;
    }
    if (requireFuture) {
      throw BusinessException.validation("定时须晚于当前时间");
    }
    return now;
  }

  private static LocalDateTime parseSchedule(String raw) {
    String value = raw.trim();
    try {
      if (value.length() == 16) {
        return LocalDateTime.parse(value, SCHEDULE_MIN);
      }
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException ex) {
      throw BusinessException.validation("定时时间不合法");
    }
  }

  private void bindMedia(long postId, List<MediaAsset> assets) {
    int i = 0;
    for (MediaAsset asset : assets) {
      PostMedia link = new PostMedia();
      link.setId(IdGenerator.nextId());
      link.setPostId(postId);
      link.setMediaId(asset.getId());
      link.setSortOrder(i);
      link.setRole(roleFor(asset, i));
      postMediaMapper.insert(link);
      i++;
    }
  }

  private boolean follows(Long viewerId, Long authorId) {
    if (viewerId == null || authorId == null) {
      return false;
    }
    try {
      List<Long> ids = userClient.followeeIds(internalToken, viewerId);
      return ids != null && ids.contains(authorId);
    } catch (Exception ex) {
      log.warn("查关注失败 viewerId={} authorId={}", viewerId, authorId, ex);
      return false;
    }
  }
}
