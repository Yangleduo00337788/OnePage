package com.onepage.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.user.client.ContentClient;
import com.onepage.user.dto.FollowHit;
import com.onepage.user.dto.PostCursorPage;
import com.onepage.user.dto.UserBriefView;
import com.onepage.user.dto.UserCursorPage;
import com.onepage.user.dto.UserProfileView;
import com.onepage.user.entity.UserProfile;
import com.onepage.user.entity.UserStat;
import com.onepage.user.mapper.UserFollowMapper;
import com.onepage.user.mapper.UserProfileMapper;
import com.onepage.user.mapper.UserStatMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资料与计数。头像/封面只存 MinIO 的 objectKey，URL 用 {@code onepage.media-public-base} 拼出来。
 * 换绑头像时要向 content-service 确认：文件属于当前用户且 status=已上传，防止盗用别人的 mediaId。
 */
@Service
public class UserService {
  private final UserProfileMapper profileMapper;
  private final UserStatMapper statMapper;
  private final UserFollowMapper followMapper;
  private final ContentClient contentClient;
  private final String internalToken;
  private final String mediaPublicBase;

  public UserService(
      UserProfileMapper profileMapper,
      UserStatMapper statMapper,
      UserFollowMapper followMapper,
      ContentClient contentClient,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.media-public-base}") String mediaPublicBase) {
    this.profileMapper = profileMapper;
    this.statMapper = statMapper;
    this.followMapper = followMapper;
    this.contentClient = contentClient;
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

  public UserSummary summary(Long userId) {
    return toSummary(requireProfileByUserId(userId));
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

  public UserCursorPage following(String pageId, Long viewerId, String cursor, Integer limit) {
    UserProfile owner = requireProfileByPageId(pageId);
    return pageRelations(owner.getUserId(), viewerId, cursor, limit, true);
  }

  public UserCursorPage followers(String pageId, Long viewerId, String cursor, Integer limit) {
    UserProfile owner = requireProfileByPageId(pageId);
    return pageRelations(owner.getUserId(), viewerId, cursor, limit, false);
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
    if (profile == null) {
      throw BusinessException.notFound("用户不存在");
    }
    return profile;
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
