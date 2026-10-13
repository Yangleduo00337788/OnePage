package com.onepage.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.user.client.ContentClient;
import com.onepage.user.dto.PostCursorPage;
import com.onepage.user.dto.UserProfileView;
import com.onepage.user.entity.UserProfile;
import com.onepage.user.entity.UserStat;
import com.onepage.user.mapper.UserProfileMapper;
import com.onepage.user.mapper.UserStatMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 资料与计数。头像/封面只存 MinIO 的 objectKey，URL 用 {@code onepage.media-public-base} 拼出来。
 * 换绑头像时要向 content-service 确认：文件属于当前用户且 status=已上传，防止盗用别人的 mediaId。
 */
@Service
public class UserService {
  private final UserProfileMapper profileMapper;
  private final UserStatMapper statMapper;
  private final ContentClient contentClient;
  private final String internalToken;
  private final String mediaPublicBase;

  public UserService(
      UserProfileMapper profileMapper,
      UserStatMapper statMapper,
      ContentClient contentClient,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.media-public-base}") String mediaPublicBase) {
    this.profileMapper = profileMapper;
    this.statMapper = statMapper;
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
    return toView(requireProfileByUserId(userId));
  }

  public UserProfileView byPageId(String pageId) {
    return toView(requireProfileByPageId(pageId));
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
    return toView(profile);
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

  private UserProfileView toView(UserProfile profile) {
    UserStat stat = statMapper.selectById(profile.getUserId());
    if (stat == null) {
      stat = new UserStat();
      stat.setFollowingCount(0);
      stat.setFollowerCount(0);
      stat.setLikeReceivedCount(0);
      stat.setFavoriteReceivedCount(0);
    }
    return new UserProfileView(
        String.valueOf(profile.getUserId()),
        profile.getPageId(),
        profile.getNickname(),
        publicUrl(profile.getAvatarObjectKey()),
        publicUrl(profile.getCoverObjectKey()),
        profile.getBio() == null ? "" : profile.getBio(),
        profile.getGender() == null ? 0 : profile.getGender(),
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
