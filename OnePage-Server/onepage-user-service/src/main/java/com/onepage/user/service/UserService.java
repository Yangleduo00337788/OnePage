package com.onepage.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.common.tx.AfterCommit;
import com.onepage.user.client.ContentClient;
import com.onepage.user.client.NotificationClient;
import com.onepage.user.dto.FollowHit;
import com.onepage.user.dto.PostCursorPage;
import com.onepage.user.dto.ReceivedCursorPage;
import com.onepage.user.dto.ReceivedItemView;
import com.onepage.user.dto.UserBriefView;
import com.onepage.user.dto.UserCursorPage;
import com.onepage.user.dto.UserProfileView;
import com.onepage.user.entity.UserProfile;
import com.onepage.user.entity.UserStat;
import com.onepage.user.mapper.UserFollowMapper;
import com.onepage.user.mapper.UserProfileMapper;
import com.onepage.user.mapper.UserStatMapper;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资料与计数。头像/封面只存 MinIO 的 objectKey，URL 用 {@code onepage.media-public-base} 拼出来。
 * 换绑头像时要向 content-service 确认：文件属于当前用户且 status=已上传，防止盗用别人的 mediaId。
 */
@Service
public class UserService {
  private static final Logger log = LoggerFactory.getLogger(UserService.class);
  private static final int SUGGEST_MAX = 8;

  private final UserProfileMapper profileMapper;
  private final UserStatMapper statMapper;
  private final UserFollowMapper followMapper;
  private final ContentClient contentClient;
  private final NotificationClient notificationClient;
  private final String internalToken;
  private final String mediaPublicBase;

  public UserService(
      UserProfileMapper profileMapper,
      UserStatMapper statMapper,
      UserFollowMapper followMapper,
      ContentClient contentClient,
      NotificationClient notificationClient,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.media-public-base}") String mediaPublicBase) {
    this.profileMapper = profileMapper;
    this.statMapper = statMapper;
    this.followMapper = followMapper;
    this.contentClient = contentClient;
    this.notificationClient = notificationClient;
    this.internalToken = internalToken;
    this.mediaPublicBase = mediaPublicBase.endsWith("/")
        ? mediaPublicBase.substring(0, mediaPublicBase.length() - 1)
        : mediaPublicBase;
  }

  /** 幂等：同一 userId 再调一次直接返回已有资料（注册重试不会插两条）。 */
  public UserSummary createProfile(Long userId, String nickname) {
    UserProfile existing = profileMapper.selectById(userId);
    if (existing != null) {
      return toSummary(existing);
    }
    LocalDateTime now = LocalDateTime.now();
    UserProfile profile = new UserProfile();
    profile.setUserId(userId);
    profile.setPageId(uniquePageId(userId));
    profile.setNickname(StringUtils.hasText(nickname) ? nickname : "一页用户");
    profile.setGender(0);
    profile.setCreatedAt(now);
    profile.setUpdatedAt(now);
    profileMapper.insert(profile);

    UserStat stat = new UserStat();
    stat.setUserId(userId);
    stat.setFollowingCount(0);
    stat.setFollowerCount(0);
    stat.setLikeReceivedCount(0);
    stat.setFavoriteReceivedCount(0);
    stat.setUpdatedAt(now);
    statMapper.insert(stat);
    return toSummary(profile);
  }

  public List<UserSummary> briefs(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<Long> uniq = new LinkedHashSet<>();
    for (Long id : ids) {
      if (id != null) {
        uniq.add(id);
      }
      if (uniq.size() >= 100) {
        break;
      }
    }
    if (uniq.isEmpty()) {
      return List.of();
    }
    Map<Long, UserProfile> byId = profileMapper.selectBatchIds(uniq).stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));
    List<UserSummary> out = new ArrayList<>();
    for (Long id : uniq) {
      UserProfile profile = byId.get(id);
      if (profile != null) {
        out.add(toSummary(profile));
      }
    }
    return out;
  }

  public UserSummary summary(Long userId) {
    return toSummary(requireProfileByUserId(userId));
  }

  /** 按一页号或昵称精确匹配，给评论 @ 用。找不到的 key 直接丢掉。 */
  public List<UserSummary> byKeys(List<String> keys) {
    LinkedHashSet<String> uniq = new LinkedHashSet<>();
    if (keys != null) {
      for (String raw : keys) {
        if (!StringUtils.hasText(raw)) {
          continue;
        }
        String key = raw.trim();
        if (key.length() > 32) {
          continue;
        }
        uniq.add(key);
        if (uniq.size() >= 20) {
          break;
        }
      }
    }
    if (uniq.isEmpty()) {
      return List.of();
    }
    List<String> list = new ArrayList<>(uniq);
    List<UserProfile> rows = profileMapper.selectList(
        Wrappers.<UserProfile>lambdaQuery()
            .and(w -> w.in(UserProfile::getPageId, list).or().in(UserProfile::getNickname, list)));
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<Long> seen = new LinkedHashSet<>();
    List<UserSummary> out = new ArrayList<>();
    for (UserProfile profile : rows) {
      if (profile == null || profile.getUserId() == null || !seen.add(profile.getUserId())) {
        continue;
      }
      out.add(toSummary(profile));
    }
    return out;
  }

  /** 评论 @ 联想：空词给已关注；有词则前缀匹配一页号或模糊匹配昵称。 */
  public List<UserSummary> suggest(String q, Long viewerId) {
    String token = q == null ? "" : q.trim();
    if (token.startsWith("@")) {
      token = token.substring(1).trim();
    }
    if (token.length() > 32 || token.indexOf('%') >= 0 || token.indexOf('_') >= 0 || token.indexOf('\\') >= 0) {
      return List.of();
    }
    if (!StringUtils.hasText(token)) {
      if (viewerId == null) {
        return List.of();
      }
      List<Long> ids = listFolloweeIds(viewerId);
      int end = Math.min(ids.size(), SUGGEST_MAX);
      return briefs(new ArrayList<>(ids.subList(0, end)));
    }
    final String needle = token;
    List<UserProfile> rows = profileMapper.selectList(
        Wrappers.<UserProfile>lambdaQuery()
            .and(w -> w.likeRight(UserProfile::getPageId, needle).or().like(UserProfile::getNickname, needle))
            .last("LIMIT 20"));
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<UserProfile> ranked = new ArrayList<>();
    LinkedHashSet<Long> seen = new LinkedHashSet<>();
    for (UserProfile row : rows) {
      if (row != null && token.equals(row.getPageId()) && seen.add(row.getUserId())) {
        ranked.add(row);
      }
    }
    for (UserProfile row : rows) {
      if (row != null && token.equals(row.getNickname()) && seen.add(row.getUserId())) {
        ranked.add(row);
      }
    }
    for (UserProfile row : rows) {
      if (row != null && row.getUserId() != null && seen.add(row.getUserId())) {
        ranked.add(row);
      }
    }
    List<UserSummary> out = new ArrayList<>();
    for (UserProfile row : ranked) {
      if (viewerId != null && viewerId.equals(row.getUserId())) {
        continue;
      }
      out.add(toSummary(row));
      if (out.size() >= SUGGEST_MAX) {
        break;
      }
    }
    return out;
  }

  public UserProfileView me(Long userId) {
    return toView(requireProfileByUserId(userId), userId);
  }

  public UserProfileView byPageId(String pageId, Long viewerId) {
    return toView(requireProfileByPageId(pageId), viewerId);
  }

  public PostCursorPage postsByPageId(String pageId, Long viewerId, String cursor, Integer limit) {
    UserProfile profile = requireProfileByPageId(pageId);
    return contentClient.listAuthorPosts(internalToken, profile.getUserId(), cursor, limit, viewerId);
  }

  public PostCursorPage myFavorites(long userId, String cursor, Integer limit) {
    return contentClient.listFavorites(internalToken, userId, cursor, limit);
  }

  public PostCursorPage myLikes(long userId, String cursor, Integer limit) {
    return contentClient.listLikes(internalToken, userId, cursor, limit);
  }

  public PostCursorPage myHistory(long userId, String cursor, Integer limit) {
    return contentClient.listHistory(internalToken, userId, cursor, limit);
  }

  public ReceivedCursorPage myLikesReceived(long userId, String cursor, Integer limit) {
    return hydrateReceived(contentClient.listLikesReceived(internalToken, userId, cursor, limit), userId);
  }

  public ReceivedCursorPage myFavoritesReceived(long userId, String cursor, Integer limit) {
    return hydrateReceived(contentClient.listFavoritesReceived(internalToken, userId, cursor, limit), userId);
  }

  public UserProfileView updateMe(Long userId, String nickname, String bio, Integer gender,
                                  String avatarMediaId, String coverMediaId) {
    UserProfile profile = requireProfileByUserId(userId);
    if (StringUtils.hasText(nickname)) {
      profile.setNickname(nickname);
    }
    if (bio != null) {
      profile.setBio(bio);
    }
    if (gender != null) {
      profile.setGender(gender);
    }
    if (StringUtils.hasText(avatarMediaId)) {
      profile.setAvatarObjectKey(objectKeyOwnedBy(userId, avatarMediaId));
    }
    if (StringUtils.hasText(coverMediaId)) {
      profile.setCoverObjectKey(objectKeyOwnedBy(userId, coverMediaId));
    }
    profile.setUpdatedAt(LocalDateTime.now());
    profileMapper.updateById(profile);
    return toView(profile, userId);
  }

  @Transactional
  public boolean follow(long followerId, String pageId) {
    UserProfile target = requireProfileByPageId(pageId);
    long followeeId = target.getUserId();
    if (followerId == followeeId) {
      throw BusinessException.validation("不能关注自己");
    }
    LocalDateTime now = LocalDateTime.now();
    int inserted = followMapper.insertIgnore(followerId, followeeId, now);
    if (inserted <= 0) {
      return false;
    }
    statMapper.bumpFollowing(followerId, 1, now);
    statMapper.bumpFollower(followeeId, 1, now);
    AfterCommit.run(() -> {
      try {
        notificationClient.ingest(
            internalToken,
            new NotificationClient.IngestRequest(
                followeeId,
                "follow",
                followerId,
                "follow:" + followerId + ":" + followeeId,
                null,
                null,
                null));
      } catch (RuntimeException ex) {
        log.warn("notify follow to {} failed", followeeId, ex);
      }
    });
    return true;
  }

  @Transactional
  public void unfollow(long followerId, String pageId) {
    UserProfile target = requireProfileByPageId(pageId);
    long followeeId = target.getUserId();
    int deleted = followMapper.deletePair(followerId, followeeId);
    if (deleted <= 0) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    statMapper.bumpFollowing(followerId, -1, now);
    statMapper.bumpFollower(followeeId, -1, now);
  }

  public List<Long> listFolloweeIds(long userId) {
    List<Long> ids = followMapper.listFolloweeIds(userId);
    return ids == null ? List.of() : ids;
  }

  public long countProfiles() {
    Long n = profileMapper.selectCount(Wrappers.lambdaQuery());
    return n == null ? 0L : n;
  }

  /** 运营用户列表。q 可空，空则全表倒序。 */
  public UserSummaryPage opsPage(String q, String cursor, Integer limit) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    var query = Wrappers.<UserProfile>lambdaQuery();
    if (StringUtils.hasText(q)) {
      String text = q.trim();
      query.and(w -> w.like(UserProfile::getPageId, text).or().like(UserProfile::getNickname, text));
    }
    if (StringUtils.hasText(cursor)) {
      String[] parts = cursor.split("_", 2);
      if (parts.length != 2) {
        throw BusinessException.validation("cursor 不合法");
      }
      try {
        LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneId.systemDefault());
        long id = Long.parseLong(parts[1]);
        query.and(w -> w.lt(UserProfile::getCreatedAt, at)
            .or(inner -> inner.eq(UserProfile::getCreatedAt, at).lt(UserProfile::getUserId, id)));
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("cursor 不合法");
      }
    }
    query.orderByDesc(UserProfile::getCreatedAt).orderByDesc(UserProfile::getUserId).last("LIMIT " + (size + 1));
    List<UserProfile> rows = profileMapper.selectList(query);
    boolean hasMore = rows.size() > size;
    if (hasMore) {
      rows = rows.subList(0, size);
    }
    List<UserSummary> items = new ArrayList<>();
    for (UserProfile row : rows) {
      items.add(toSummary(row));
    }
    String next = null;
    if (hasMore && !rows.isEmpty()) {
      UserProfile last = rows.getLast();
      if (last.getCreatedAt() != null && last.getUserId() != null) {
        next = last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + "_" + last.getUserId();
      }
    }
    return new UserSummaryPage(items, next, hasMore);
  }

  public record UserSummaryPage(List<UserSummary> items, String nextCursor, boolean hasMore) {}

  public UserCursorPage opsRelations(long userId, boolean following, String cursor, Integer limit) {
    if (profileMapper.selectById(userId) == null) {
      throw BusinessException.notFound("用户不存在");
    }
    return pageRelations(userId, null, cursor, limit, following);
  }

  @Transactional
  public void opsRemoveFollow(long followerId, long followeeId) {
    if (followerId == followeeId) {
      throw BusinessException.validation("不能取消自己关注自己");
    }
    int deleted = followMapper.deletePair(followerId, followeeId);
    if (deleted <= 0) {
      throw BusinessException.notFound("关注关系不存在");
    }
    LocalDateTime now = LocalDateTime.now();
    statMapper.bumpFollowing(followerId, -1, now);
    statMapper.bumpFollower(followeeId, -1, now);
  }

  public UserCursorPage following(String pageId, Long viewerId, String cursor, Integer limit) {
    UserProfile owner = requireProfileByPageId(pageId);
    return pageRelations(owner.getUserId(), viewerId, cursor, limit, true);
  }

  public UserCursorPage followers(String pageId, Long viewerId, String cursor, Integer limit) {
    UserProfile owner = requireProfileByPageId(pageId);
    return pageRelations(owner.getUserId(), viewerId, cursor, limit, false);
  }

  /** 运营隐藏头像/封面后清掉资料上的 object key，列表不再拼出 URL。 */
  @Transactional
  public void clearObjectKey(String objectKey) {
    if (!StringUtils.hasText(objectKey)) {
      return;
    }
    String key = objectKey.trim();
    profileMapper.update(
        null,
        Wrappers.<UserProfile>lambdaUpdate()
            .eq(UserProfile::getAvatarObjectKey, key)
            .set(UserProfile::getAvatarObjectKey, null));
    profileMapper.update(
        null,
        Wrappers.<UserProfile>lambdaUpdate()
            .eq(UserProfile::getCoverObjectKey, key)
            .set(UserProfile::getCoverObjectKey, null));
  }

  private String objectKeyOwnedBy(Long userId, String mediaId) {
    ContentClient.MediaInfo info = contentClient.getMedia(internalToken, Long.parseLong(mediaId));
    if (info == null || !userId.equals(info.ownerId()) || info.status() == null || info.status() != 1) {
      throw BusinessException.validation("媒体不可用");
    }
    return info.objectKey();
  }

  private UserProfile requireProfileByPageId(String pageId) {
    UserProfile profile = profileMapper.selectOne(
        Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getPageId, pageId));
    if (profile != null) {
      return profile;
    }
    List<UserProfile> byNick = profileMapper.selectList(
        Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getNickname, pageId));
    if (byNick != null && byNick.size() == 1) {
      return byNick.get(0);
    }
    throw BusinessException.notFound("用户不存在");
  }

  private UserProfile requireProfileByUserId(Long userId) {
    UserProfile profile = profileMapper.selectById(userId);
    if (profile == null) {
      throw BusinessException.notFound("用户不存在");
    }
    return profile;
  }

  /** pageId 冲突时在后面加 36 进制后缀，保证对外短码仍唯一。 */
  private String uniquePageId(Long userId) {
    String candidate = IdGenerator.pageId(userId);
    int i = 0;
    while (profileMapper.selectOne(
        Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getPageId, candidate)) != null) {
      candidate = IdGenerator.pageId(userId) + Integer.toString(i++, 36).toUpperCase();
    }
    return candidate;
  }

  private UserSummary toSummary(UserProfile profile) {
    return new UserSummary(
        String.valueOf(profile.getUserId()),
        profile.getPageId(),
        profile.getNickname(),
        publicUrl(profile.getAvatarObjectKey()));
  }

  private UserCursorPage pageRelations(
      long ownerId, Long viewerId, String cursor, Integer limit, boolean following) {
    int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    LocalDateTime cursorAt = null;
    Long cursorUserId = null;
    if (StringUtils.hasText(cursor)) {
      String[] parts = cursor.split("_", 2);
      if (parts.length != 2) {
        throw BusinessException.validation("cursor 不合法");
      }
      try {
        cursorAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneId.systemDefault());
        cursorUserId = Long.parseLong(parts[1]);
      } catch (NumberFormatException ex) {
        throw BusinessException.validation("cursor 不合法");
      }
    }
    List<FollowHit> hits = following
        ? followMapper.pageFollowing(ownerId, cursorAt, cursorUserId, size + 1)
        : followMapper.pageFollowers(ownerId, cursorAt, cursorUserId, size + 1);
    boolean hasMore = hits.size() > size;
    if (hasMore) {
      hits = hits.subList(0, size);
    }
    List<Long> ids = new ArrayList<>();
    for (FollowHit hit : hits) {
      if (hit.getUserId() != null) {
        ids.add(hit.getUserId());
      }
    }
    Map<Long, UserProfile> byId = ids.isEmpty()
        ? Map.of()
        : profileMapper.selectBatchIds(ids).stream()
            .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));
    Set<Long> mine = viewerId == null ? Set.of() : new HashSet<>(listFolloweeIds(viewerId));
    List<UserBriefView> items = new ArrayList<>();
    for (Long id : ids) {
      UserProfile profile = byId.get(id);
      if (profile != null) {
        items.add(toBrief(profile, viewerId != null && mine.contains(id)));
      }
    }
    String next = null;
    if (hasMore && !hits.isEmpty()) {
      FollowHit last = hits.getLast();
      if (last.getAt() != null && last.getUserId() != null) {
        next = last.getAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + "_" + last.getUserId();
      }
    }
    return new UserCursorPage(items, next, hasMore);
  }

  private ReceivedCursorPage hydrateReceived(ContentClient.ReceivedPage page, long viewerId) {
    if (page == null || page.items() == null || page.items().isEmpty()) {
      return new ReceivedCursorPage(List.of(), page == null ? null : page.nextCursor(), page != null && page.hasMore());
    }
    List<Long> ids = new ArrayList<>();
    for (ContentClient.ReceivedHit hit : page.items()) {
      if (hit == null || !StringUtils.hasText(hit.userId())) {
        continue;
      }
      try {
        ids.add(Long.parseLong(hit.userId()));
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    Map<Long, UserProfile> byId = ids.isEmpty()
        ? Map.of()
        : profileMapper.selectBatchIds(ids).stream()
            .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));
    Set<Long> mine = new HashSet<>(listFolloweeIds(viewerId));
    List<ReceivedItemView> items = new ArrayList<>();
    for (ContentClient.ReceivedHit hit : page.items()) {
      if (hit == null || !StringUtils.hasText(hit.userId())) {
        continue;
      }
      long actorId;
      try {
        actorId = Long.parseLong(hit.userId());
      } catch (NumberFormatException ex) {
        continue;
      }
      UserProfile profile = byId.get(actorId);
      UserBriefView actor = profile == null
          ? new UserBriefView(hit.userId(), "", "用户", "", mine.contains(actorId))
          : toBrief(profile, mine.contains(actorId));
      items.add(new ReceivedItemView(
          actor,
          hit.postId() == null ? "" : hit.postId(),
          hit.postTitle() == null ? "" : hit.postTitle(),
          hit.createdAt() == null ? "" : hit.createdAt()));
    }
    return new ReceivedCursorPage(items, page.nextCursor(), page.hasMore());
  }

  private UserBriefView toBrief(UserProfile profile, boolean followedByMe) {
    return new UserBriefView(
        String.valueOf(profile.getUserId()),
        profile.getPageId(),
        profile.getNickname(),
        publicUrl(profile.getAvatarObjectKey()),
        followedByMe);
  }

  private UserProfileView toView(UserProfile profile, Long viewerId) {
    UserStat stat = statMapper.selectById(profile.getUserId());
    if (stat == null) {
      stat = new UserStat();
      stat.setFollowingCount(0);
      stat.setFollowerCount(0);
      stat.setLikeReceivedCount(0);
      stat.setFavoriteReceivedCount(0);
    }
    boolean followed = viewerId != null
        && !viewerId.equals(profile.getUserId())
        && followMapper.existsPair(viewerId, profile.getUserId()) > 0;
    return new UserProfileView(
        String.valueOf(profile.getUserId()),
        profile.getPageId(),
        profile.getNickname(),
        publicUrl(profile.getAvatarObjectKey()),
        publicUrl(profile.getCoverObjectKey()),
        profile.getBio() == null ? "" : profile.getBio(),
        profile.getGender() == null ? 0 : profile.getGender(),
        followed,
        new UserProfileView.Stats(
            n(stat.getFollowingCount()),
            n(stat.getFollowerCount()),
            n(stat.getLikeReceivedCount()),
            n(stat.getFavoriteReceivedCount())));
  }

  private String publicUrl(String objectKey) {
    if (!StringUtils.hasText(objectKey)) {
      return "";
    }
    return mediaPublicBase + "/" + objectKey;
  }

  private int n(Integer v) {
    return v == null ? 0 : v;
  }
}
