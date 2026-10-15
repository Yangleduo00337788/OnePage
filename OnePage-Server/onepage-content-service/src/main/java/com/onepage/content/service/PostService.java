package com.onepage.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.client.ImClient;
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
    this.objectMapper = objectMapper;
    this.imClient = imClient;
    this.mediaPublicBase = trimSlash(mediaPublicBase);
    this.frontendUrl = trimSlash(frontendUrl);
    this.internalToken = internalToken;
  }

  @Transactional
  public FeedDtos.CreateResult publish(long userId, FeedDtos.PublishSpec spec) {
    if (!StringUtils.hasText(spec.body())) {
      throw BusinessException.validation("正文不能为空");
    }
    String type = spec.postType() == null ? "" : spec.postType().trim().toLowerCase();
    if (!POST_TYPES.contains(type)) {
      throw BusinessException.validation("postType 不支持");
    }
    String cat = spec.category() == null ? "" : spec.category().trim();
    if (!catalogService.isEnabledCategory(cat)) {
      throw BusinessException.validation("请选择笔记分类");
    }
    List<MediaAsset> assets = loadOwnedReadyMedia(userId, spec.mediaIds());
    if ("text".equals(type) && !assets.isEmpty()) {
      throw BusinessException.validation("纯文本笔记不能带媒体");
    }
    if (!"text".equals(type) && assets.isEmpty()) {
      throw BusinessException.validation("该类型需要媒体");
    }
    LocalDateTime now = LocalDateTime.now();
    long postId = IdGenerator.nextId();
    Post post = new Post();
    post.setId(postId);
    post.setAuthorId(userId);
    post.setTitle(spec.title() == null ? "" : spec.title().trim());
    post.setBody(spec.body().trim());
    post.setPostType(type);
    post.setCategory(cat);
    post.setOriginal(Boolean.TRUE.equals(spec.original()) ? 1 : 0);
    post.setContentKind(normalizeKind(spec.contentKind()));
    applyCampaign(post, spec.eventId());
    post.setStatus("published");
    post.setVisibility(parseVisibility(spec.visibility()));
    post.setPublishedAt(now);
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    postMapper.insert(post);
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
    writeOutbox("post", String.valueOf(postId), "post.published", Map.of(
        "postId", String.valueOf(postId),
        "authorId", String.valueOf(userId),
        "postType", type));
    catalogService.rememberHashtags(spec.body());
    return new FeedDtos.CreateResult(String.valueOf(postId), now.toString());
  }

  @Transactional
  public FeedDtos.PostDetail update(long userId, long postId, FeedDtos.PublishSpec spec) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null || !"published".equals(post.getStatus())) {
      throw BusinessException.notFound("笔记不存在");
    }
    if (post.getAuthorId() == null || post.getAuthorId() != userId) {
      throw BusinessException.forbidden("只能修改自己的笔记");
    }
    if (!StringUtils.hasText(spec.body())) {
      throw BusinessException.validation("正文不能为空");
    }
    String type = spec.postType() == null ? "" : spec.postType().trim().toLowerCase();
    if (!POST_TYPES.contains(type)) {
      throw BusinessException.validation("postType 不支持");
    }
    String cat = spec.category() == null ? "" : spec.category().trim();
    if (!catalogService.isEnabledCategory(cat)) {
      throw BusinessException.validation("请选择笔记分类");
    }
    List<MediaAsset> assets = loadOwnedReadyMedia(userId, spec.mediaIds());
    if ("text".equals(type) && !assets.isEmpty()) {
      throw BusinessException.validation("纯文本笔记不能带媒体");
    }
    if (!"text".equals(type) && assets.isEmpty()) {
      throw BusinessException.validation("该类型需要媒体");
    }
    LocalDateTime now = LocalDateTime.now();
    post.setTitle(spec.title() == null ? "" : spec.title().trim());
    post.setBody(spec.body().trim());
    post.setPostType(type);
    post.setCategory(cat);
    post.setOriginal(Boolean.TRUE.equals(spec.original()) ? 1 : 0);
    post.setContentKind(normalizeKind(spec.contentKind()));
    applyCampaign(post, spec.eventId());
    post.setVisibility(parseVisibility(spec.visibility()));
    post.setUpdatedAt(now);
    postMapper.updateById(post);
    postMediaMapper.delete(Wrappers.<PostMedia>lambdaQuery().eq(PostMedia::getPostId, postId));
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
    writeOutbox("post", String.valueOf(postId), "post.updated", Map.of(
        "postId", String.valueOf(postId),
        "authorId", String.valueOf(userId)));
    catalogService.rememberHashtags(spec.body());
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
        iso(post.getPublishedAt()));
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
    post.setStatus("deleted");
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
        iso(post.getPublishedAt()));
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
    post.setStatus("deleted");
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
        .set(Post::getStatus, "published")
        .set(Post::getDeletedAt, null)
        .set(Post::getUpdatedAt, now);
    postMapper.update(null, update);
    writeOutbox("post", String.valueOf(postId), "post.restored", Map.of("postId", String.valueOf(postId)));
  }

  /** 运营笔记列表：含已下架。taken=live 仅在线，down 仅下架。 */
  public FeedDtos.OpsPostPage opsPage(String q, String taken, String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LambdaQueryWrapper<Post> query = Wrappers.<Post>lambdaQuery();
    if ("down".equals(taken)) {
      query.isNotNull(Post::getDeletedAt);
    } else if ("live".equals(taken)) {
      query.isNull(Post::getDeletedAt);
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
          iso(post.getPublishedAt())));
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
        .eq(Post::getStatus, "published")
        .isNull(Post::getDeletedAt);
    if (!self) {
      query.eq(Post::getVisibility, 0);
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
    post.setVisibility(parseVisibility(visibility));
    post.setUpdatedAt(LocalDateTime.now());
    postMapper.updateById(post);
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
    if (post == null || post.getDeletedAt() != null || !"published".equals(post.getStatus())) {
      throw BusinessException.notFound("笔记不存在");
    }
    return post;
  }

  public Post requireVisible(long postId, Long viewerId) {
    Post post = requirePublished(postId);
    if (Integer.valueOf(1).equals(post.getVisibility())) {
      if (viewerId == null || !viewerId.equals(post.getAuthorId())) {
        throw BusinessException.notFound("笔记不存在");
      }
    }
    return post;
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
        iso(post.getPublishedAt()));
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
    return visibility != null && visibility == 1 ? "self" : "public";
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
      throw BusinessException.validation("好友可见尚未接入");
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
}
