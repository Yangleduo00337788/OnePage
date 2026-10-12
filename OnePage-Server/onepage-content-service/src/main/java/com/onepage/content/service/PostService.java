package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.common.api.BusinessException;
import com.onepage.common.id.IdGenerator;
import com.onepage.content.dto.FeedDtos;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发帖、详情、软删、浏览。发布与 Outbox 同一事务；MQ 消费留到后续。
 */
@Service
public class PostService {
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
  private final ObjectMapper objectMapper;
  private final String mediaPublicBase;
  private final String frontendUrl;

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
      ObjectMapper objectMapper,
      @Value("${onepage.media-public-base}") String mediaPublicBase,
      @Value("${onepage.frontend-url}") String frontendUrl) {
    this.postMapper = postMapper;
    this.postMediaMapper = postMediaMapper;
    this.mediaAssetMapper = mediaAssetMapper;
    this.outboxEventMapper = outboxEventMapper;
    this.likeMapper = likeMapper;
    this.favoriteMapper = favoriteMapper;
    this.commentMapper = commentMapper;
    this.postViewMapper = postViewMapper;
    this.authorLookup = authorLookup;
    this.objectMapper = objectMapper;
    this.mediaPublicBase = trimSlash(mediaPublicBase);
    this.frontendUrl = trimSlash(frontendUrl);
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
    if (!CATEGORIES.contains(cat)) {
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
    return new FeedDtos.CreateResult(String.valueOf(postId), now.toString());
  }

  public FeedDtos.PostDetail detail(Long viewerId, long postId) {
    Post post = requireVisible(postId, viewerId);
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

  public void recordView(long userId, long postId) {
    requireVisible(postId, userId);
    postViewMapper.upsert(userId, postId, LocalDateTime.now());
  }

  public String shareLink(long postId, Long viewerId) {
    requireVisible(postId, viewerId);
    return frontendUrl + "/?post=" + postId;
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
        iso(post.getPublishedAt()));
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

  private static void applyCampaign(Post post, String eventId) {
    if (!StringUtils.hasText(eventId)) {
      post.setEventId("");
      post.setEventTitle("");
      return;
    }
    String id = eventId.trim();
    String title = CampaignCatalog.TITLES.get(id);
    if (title == null) {
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
      if (asset == null) {
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
      if (asset == null || asset.getOwnerId() == null || asset.getOwnerId() != userId || asset.getStatus() == null || asset.getStatus() != 1) {
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
