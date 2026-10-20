package com.onepage.live.service;

import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.live.client.ContentClient;
import com.onepage.live.client.ImClient;
import com.onepage.live.client.UserClient;
import com.onepage.live.dto.LiveDtos;
import com.onepage.live.dto.LiveHit;
import com.onepage.live.entity.LiveRoom;
import com.onepage.live.entity.LiveSession;
import com.onepage.live.mapper.LiveRoomMapper;
import com.onepage.live.mapper.LiveSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 房间与场次。同一主播同时只能一场 live。 */
@Service
public class LiveService {
  private static final Logger log = LoggerFactory.getLogger(LiveService.class);
  private static final long PRESENCE_TTL_MS = 25_000L;
  private static final Duration PRESENCE_EXPIRE = Duration.ofMinutes(2);
  private static final int NOTICE_MAX = 200;
  private static final String UNPUBLISH_ZSET = "live:unpublish";
  private static final Duration INGEST_EXPIRE = Duration.ofDays(2);
  private final LiveRoomMapper roomMapper;
  private final LiveSessionMapper sessionMapper;
  private final LiveChatService chatService;
  private final UserClient userClient;
  private final ContentClient contentClient;
  private final ImClient imClient;
  private final StringRedisTemplate redis;
  private final LiveIngestProbe ingestProbe;
  private final LiveSearchIndexer searchIndexer;
  private final CommunityPolicyCache policyCache;
  private final String internalToken;
  private final String pushBase;
  private final String hlsBase;
  private final String mediaPublicBase;
  private final String frontendUrl;
  private final long unpublishGraceMs;

  public LiveService(
      LiveRoomMapper roomMapper,
      LiveSessionMapper sessionMapper,
      LiveChatService chatService,
      UserClient userClient,
      ContentClient contentClient,
      ImClient imClient,
      StringRedisTemplate redis,
      LiveIngestProbe ingestProbe,
      @Lazy LiveSearchIndexer searchIndexer,
      CommunityPolicyCache policyCache,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.live-push-base}") String pushBase,
      @Value("${onepage.live-hls-base}") String hlsBase,
      @Value("${onepage.media-public-base}") String mediaPublicBase,
      @Value("${onepage.frontend-url}") String frontendUrl,
      @Value("${onepage.unpublish-grace-seconds}") long unpublishGraceSeconds) {
    this.roomMapper = roomMapper;
    this.sessionMapper = sessionMapper;
    this.chatService = chatService;
    this.userClient = userClient;
    this.contentClient = contentClient;
    this.imClient = imClient;
    this.redis = redis;
    this.ingestProbe = ingestProbe;
    this.searchIndexer = searchIndexer;
    this.policyCache = policyCache;
    this.internalToken = internalToken;
    this.pushBase = pushBase;
    this.hlsBase = hlsBase;
    this.mediaPublicBase = mediaPublicBase == null ? "" : mediaPublicBase.replaceAll("/+$", "");
    this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    this.unpublishGraceMs = Math.max(0L, unpublishGraceSeconds) * 1000L;
  }

  public LiveDtos.RoomCreated createRoom(long ownerId, LiveDtos.RoomSpec spec) {
    String title = spec == null || spec.title() == null ? "" : spec.title().trim();
    if (title.isEmpty() || title.length() > 100) {
      throw BusinessException.validation("请填写直播标题");
    }
    policyCache.assertClean(title);
    LiveRoom room = new LiveRoom();
    room.setId(IdGenerator.nextId());
    room.setOwnerId(ownerId);
    room.setTitle(title);
    room.setCategory(spec.category() == null ? "" : spec.category().trim());
    String coverId = spec.coverMediaId() == null ? "" : spec.coverMediaId().trim();
    room.setCoverObjectKey(coverId.isEmpty() ? "" : objectKeyOwnedBy(ownerId, coverId));
    room.setNotice(normalizeNotice(spec.notice()));
    room.setCreatedAt(LocalDateTime.now());
    roomMapper.insert(room);
    return new LiveDtos.RoomCreated(String.valueOf(room.getId()));
  }

  public LiveDtos.LiveStart start(long ownerId, long roomId) {
    LiveRoom room = roomMapper.selectById(roomId);
    if (room == null) {
      throw BusinessException.notFound("房间不存在");
    }
    if (!ownerIdEquals(room.getOwnerId(), ownerId)) {
      throw BusinessException.forbidden("只能开自己的直播");
    }
    LiveHit existing = sessionMapper.findLiveByOwner(ownerId);
    if (existing != null) {
      if (!existing.getRoomId().equals(roomId)) {
        throw BusinessException.conflict("已有一场直播进行中");
      }
      searchIndexer.sync(existing.getSessionId());
      return toStart(existing);
    }
    LiveSession session = new LiveSession();
    session.setId(IdGenerator.nextId());
    session.setRoomId(roomId);
    session.setStreamKey(UUID.randomUUID().toString().replace("-", ""));
    session.setStatus("live");
    session.setStartedAt(LocalDateTime.now());
    sessionMapper.insert(session);
    LiveHit hit = sessionMapper.findHit(session.getId());
    searchIndexer.sync(session.getId());
    return toStart(hit);
  }

  public LiveDtos.LiveStart mine(long ownerId) {
    LiveHit existing = sessionMapper.findLiveByOwner(ownerId);
    if (existing == null) {
      return null;
    }
    return toStart(existing);
  }

  public void end(long ownerId, long roomId) {
    LiveRoom room = roomMapper.selectById(roomId);
    if (room == null) {
      throw BusinessException.notFound("房间不存在");
    }
    if (!ownerIdEquals(room.getOwnerId(), ownerId)) {
      throw BusinessException.forbidden("只能关自己的直播");
    }
    LiveHit live = sessionMapper.findLiveByOwner(ownerId);
    if (live == null || !live.getRoomId().equals(roomId)) {
      return;
    }
    closeLive(live);
  }

  public void onPublish(String path, String streamKey) {
    LiveHit live = findLiveByHook(path, streamKey);
    if (live == null) {
      return;
    }
    rememberIngest(live.getSessionId());
    redis.opsForZSet().remove(UNPUBLISH_ZSET, String.valueOf(live.getSessionId()));
  }

  public void onUnpublish(String path, String streamKey) {
    LiveHit live = findLiveByHook(path, streamKey);
    if (live == null) {
      return;
    }
    rememberIngest(live.getSessionId());
    scheduleUnpublish(live);
  }

  public void watchIngest() {
    for (LiveHit live : sessionMapper.listLive("", "")) {
      if (live == null || live.getSessionId() == null) {
        continue;
      }
      Boolean publishing = ingestProbe.publishing(live.getStreamKey());
      if (publishing == null) {
        continue;
      }
      if (Boolean.TRUE.equals(publishing)) {
        rememberIngest(live.getSessionId());
        redis.opsForZSet().remove(UNPUBLISH_ZSET, String.valueOf(live.getSessionId()));
      } else if (Boolean.TRUE.equals(redis.hasKey(ingestKey(live.getSessionId())))) {
        scheduleUnpublish(live);
      }
    }
  }

  public void flushUnpublished() {
    long now = System.currentTimeMillis();
    Set<String> due = redis.opsForZSet().rangeByScore(UNPUBLISH_ZSET, 0, now);
    if (due == null || due.isEmpty()) {
      return;
    }
    for (String raw : due) {
      long sessionId;
      try {
        sessionId = Long.parseLong(raw);
      } catch (NumberFormatException ex) {
        redis.opsForZSet().remove(UNPUBLISH_ZSET, raw);
        continue;
      }
      LiveHit live = sessionMapper.findHit(sessionId);
      if (live == null || !"live".equals(live.getStatus())) {
        redis.opsForZSet().remove(UNPUBLISH_ZSET, raw);
        continue;
      }
      closeLive(live);
    }
  }

  public boolean hasLive(long ownerId) {
    return sessionMapper.findLiveByOwner(ownerId) != null;
  }

  public LiveDtos.NoticeView updateNotice(long ownerId, long roomId, LiveDtos.NoticeSpec spec) {
    LiveRoom room = requireOwnedRoom(ownerId, roomId);
    room.setNotice(normalizeNotice(spec == null ? null : spec.notice()));
    roomMapper.updateById(room);
    return new LiveDtos.NoticeView(nullToEmpty(room.getNotice()));
  }

  public LiveDtos.ShareLinkResult shareLink(long userId, long sessionId) {
    requireHit(sessionId);
    return new LiveDtos.ShareLinkResult(frontendUrl + "/live/" + sessionId);
  }

  public String shareIm(long userId, long sessionId, String conversationId, String targetUserId) {
    LiveHit hit = requireHit(sessionId);
    Map<Long, UserSummary> owners = loadOwners(
        hit.getOwnerId() == null ? List.of() : List.of(hit.getOwnerId()));
    UserSummary owner = owners.get(hit.getOwnerId());
    Map<String, String> card = new LinkedHashMap<>();
    card.put("sessionId", String.valueOf(hit.getSessionId()));
    card.put("title", nullToEmpty(hit.getTitle()));
    card.put("coverUrl", publicCoverUrl(hit.getCoverObjectKey()));
    card.put("authorNickname", owner == null || owner.nickname() == null ? "" : owner.nickname());
    try {
      ImClient.ShareResult result = imClient.shareLiveCard(
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
      log.warn("share live im failed sessionId={} userId={}", sessionId, userId, ex);
      throw BusinessException.validation("私信发送失败");
    }
  }

  public LiveDtos.LivePage list(String q, String category) {
    String cat = category == null || "全部".equals(category.trim()) ? "" : category.trim();
    String keyword = q == null ? "" : q.trim();
    List<LiveHit> rows = sessionMapper.listLive(keyword, cat);
    return new LiveDtos.LivePage(toItems(rows));
  }

  public LiveDtos.LiveItem get(long sessionId, boolean peek) {
    LiveHit hit = sessionMapper.findHit(sessionId);
    if (hit == null) {
      throw BusinessException.notFound("场次不存在");
    }
    List<LiveDtos.LiveItem> items = toItems(List.of(hit));
    return items.getFirst();
  }

  public List<LiveDtos.LiveItem> briefs(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<LiveHit> hits = new ArrayList<>();
    for (Long id : ids) {
      if (id == null) {
        continue;
      }
      LiveHit hit = sessionMapper.findHit(id);
      if (hit != null) {
        hits.add(hit);
      }
    }
    return toItems(hits);
  }

  public void forceEnd(long sessionId) {
    LiveHit hit = sessionMapper.findHit(sessionId);
    if (hit == null) {
      throw BusinessException.notFound("场次不存在");
    }
    closeLive(hit);
  }

  public LiveDtos.PresenceTick heartbeat(Long userId, long sessionId, LiveDtos.HeartbeatSpec spec) {
    LiveHit hit = sessionMapper.findHit(sessionId);
    if (hit == null) {
      throw BusinessException.notFound("场次不存在");
    }
    if (!"live".equals(hit.getStatus())) {
      throw BusinessException.conflict("直播已结束");
    }
    String member = presenceMember(userId, spec == null ? null : spec.presenceId());
    String key = presenceKey(sessionId);
    long now = System.currentTimeMillis();
    redis.opsForZSet().removeRangeByScore(key, 0, now - PRESENCE_TTL_MS);
    Double existed = redis.opsForZSet().score(key, member);
    redis.opsForZSet().add(key, member, now);
    redis.expire(key, PRESENCE_EXPIRE);
    long count = viewers(sessionId);
    try {
      sessionMapper.bumpPeak(sessionId, count);
    } catch (RuntimeException ex) {
      log.warn("bump peak failed sessionId={}", sessionId, ex);
    }
    LiveDtos.ChatLine join = null;
    boolean first = existed == null;
    boolean guestOrOther = userId != null && !ownerIdEquals(hit.getOwnerId(), userId);
    if (first && guestOrOther) {
      join = chatService.announceJoin(userId, sessionId);
    }
    return new LiveDtos.PresenceTick(hit.getStatus(), count, join);
  }

  private LiveDtos.LiveStart toStart(LiveHit hit) {
    String key = hit.getStreamKey();
    return new LiveDtos.LiveStart(
        String.valueOf(hit.getSessionId()),
        String.valueOf(hit.getRoomId()),
        nullToEmpty(hit.getTitle()),
        publicCoverUrl(hit.getCoverObjectKey()),
        nullToEmpty(hit.getNotice()),
        trimSlash(pushBase) + "/" + key,
        hlsPlayUrl(key),
        key);
  }

  private List<LiveDtos.LiveItem> toItems(List<LiveHit> rows) {
    List<Long> ownerIds = new ArrayList<>();
    for (LiveHit row : rows) {
      if (row.getOwnerId() != null) {
        ownerIds.add(row.getOwnerId());
      }
    }
    Map<Long, UserSummary> owners = loadOwners(ownerIds);
    List<LiveDtos.LiveItem> out = new ArrayList<>();
    for (LiveHit row : rows) {
      UserSummary owner = owners.getOrDefault(
          row.getOwnerId(), new UserSummary(String.valueOf(row.getOwnerId()), "", "主播", "", false, false, false));
      String started = row.getStartedAt() == null
          ? ""
          : row.getStartedAt().toInstant(ZoneOffset.UTC).toString();
      out.add(new LiveDtos.LiveItem(
          String.valueOf(row.getSessionId()),
          String.valueOf(row.getRoomId()),
          nullToEmpty(row.getTitle()),
          nullToEmpty(row.getCategory()),
          nullToEmpty(row.getStatus()),
          publicCoverUrl(row.getCoverObjectKey()),
          nullToEmpty(row.getNotice()),
          hlsPlayUrl(row.getStreamKey()),
          viewers(row.getSessionId()),
          owner,
          started));
    }
    return out;
  }

  private Map<Long, UserSummary> loadOwners(List<Long> ids) {
    Map<Long, UserSummary> map = new HashMap<>();
    if (ids.isEmpty()) {
      return map;
    }
    try {
      List<UserSummary> rows = userClient.briefs(internalToken, ids);
      if (rows == null) {
        return map;
      }
      for (UserSummary row : rows) {
        if (row != null && StringUtils.hasText(row.userId())) {
          map.put(Long.parseLong(row.userId()), row);
        }
      }
    } catch (RuntimeException ex) {
      log.warn("load live owners failed", ex);
    }
    return map;
  }

  private long viewers(long sessionId) {
    String key = presenceKey(sessionId);
    long now = System.currentTimeMillis();
    redis.opsForZSet().removeRangeByScore(key, 0, now - PRESENCE_TTL_MS);
    Long n = redis.opsForZSet().size(key);
    return n == null ? 0L : n;
  }

  private String presenceMember(Long userId, String presenceId) {
    if (userId != null) {
      return "u:" + userId;
    }
    if (!StringUtils.hasText(presenceId)) {
      throw BusinessException.validation("缺少在场标识");
    }
    String id = presenceId.trim();
    if (id.length() < 8 || id.length() > 64) {
      throw BusinessException.validation("在场标识无效");
    }
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (c != '-' && !Character.isLetterOrDigit(c)) {
        throw BusinessException.validation("在场标识无效");
      }
    }
    return "g:" + id;
  }

  private static boolean ownerIdEquals(Long ownerId, long userId) {
    return ownerId != null && ownerId == userId;
  }

  private static String presenceKey(long sessionId) {
    return "live:presence:" + sessionId;
  }

  private String objectKeyOwnedBy(long ownerId, String mediaId) {
    long id;
    try {
      id = Long.parseLong(mediaId);
    } catch (NumberFormatException ex) {
      throw BusinessException.validation("封面不可用");
    }
    ContentClient.MediaInfo info;
    try {
      info = contentClient.getMedia(internalToken, id);
    } catch (RuntimeException ex) {
      log.warn("load live cover media failed", ex);
      throw BusinessException.validation("封面不可用");
    }
    if (info == null
        || !ownerIdEquals(info.ownerId(), ownerId)
        || info.status() == null
        || info.status() != 1
        || !StringUtils.hasText(info.objectKey())) {
      throw BusinessException.validation("封面不可用");
    }
    return info.objectKey();
  }

  private String publicCoverUrl(String objectKey) {
    if (!StringUtils.hasText(objectKey)) {
      return "";
    }
    if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
      return objectKey;
    }
    if (mediaPublicBase.isEmpty()) {
      return "";
    }
    return mediaPublicBase + "/" + objectKey;
  }

  private String hlsPlayUrl(String key) {
    return trimSlash(hlsBase) + "/" + key + "/index.m3u8";
  }

  private static String trimSlash(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
  }

  private static String nullToEmpty(String raw) {
    return raw == null ? "" : raw;
  }

  private LiveRoom requireOwnedRoom(long ownerId, long roomId) {
    LiveRoom room = roomMapper.selectById(roomId);
    if (room == null) {
      throw BusinessException.notFound("房间不存在");
    }
    if (!ownerIdEquals(room.getOwnerId(), ownerId)) {
      throw BusinessException.forbidden("只能改自己的直播");
    }
    return room;
  }

  private LiveHit requireHit(long sessionId) {
    LiveHit hit = sessionMapper.findHit(sessionId);
    if (hit == null) {
      throw BusinessException.notFound("场次不存在");
    }
    return hit;
  }

  private void closeLive(LiveHit live) {
    LiveSession session = sessionMapper.selectById(live.getSessionId());
    if (session == null || !"live".equals(session.getStatus())) {
      forgetIngest(live.getSessionId());
      return;
    }
    session.setStatus("ended");
    session.setEndedAt(LocalDateTime.now());
    sessionMapper.updateById(session);
    forgetIngest(live.getSessionId());
    redis.delete(presenceKey(live.getSessionId()));
    searchIndexer.remove(live.getSessionId());
    log.info("live ended sessionId={}", live.getSessionId());
  }

  private void rememberIngest(long sessionId) {
    redis.opsForValue().set(ingestKey(sessionId), "1", INGEST_EXPIRE);
  }

  private void scheduleUnpublish(LiveHit live) {
    if (unpublishGraceMs <= 0L) {
      closeLive(live);
      return;
    }
    String member = String.valueOf(live.getSessionId());
    if (redis.opsForZSet().score(UNPUBLISH_ZSET, member) != null) {
      return;
    }
    redis.opsForZSet().add(UNPUBLISH_ZSET, member, System.currentTimeMillis() + unpublishGraceMs);
  }

  private void forgetIngest(long sessionId) {
    redis.delete(ingestKey(sessionId));
    redis.opsForZSet().remove(UNPUBLISH_ZSET, String.valueOf(sessionId));
  }

  private static String ingestKey(long sessionId) {
    return "live:ingest:" + sessionId;
  }

  public LiveDtos.CreatorStats creatorStats(long ownerId, String rangeRaw) {
    Window win = windowOf(rangeRaw);
    long sessions = sessionMapper.countSessions(ownerId, win.fromAt, win.toAt);
    long sessionsPrev = sessionMapper.countSessions(ownerId, win.prevFrom, win.prevTo);
    long viewers = sessionMapper.countJoiners(ownerId, win.fromAt, win.toAt);
    long viewersPrev = sessionMapper.countJoiners(ownerId, win.prevFrom, win.prevTo);
    long peak = sessionMapper.maxPeak(ownerId, win.fromAt, win.toAt);
    long peakPrev = sessionMapper.maxPeak(ownerId, win.prevFrom, win.prevTo);
    long follows = followsDuring(ownerId, win.fromAt, win.toAt);
    long followsPrev = followsDuring(ownerId, win.prevFrom, win.prevTo);
    return new LiveDtos.CreatorStats(
        win.range,
        new LiveDtos.Metric(sessions, delta(sessions, sessionsPrev)),
        new LiveDtos.Metric(viewers, delta(viewers, viewersPrev)),
        new LiveDtos.Metric(peak, delta(peak, peakPrev)),
        new LiveDtos.Metric(follows, delta(follows, followsPrev)),
        new LiveDtos.Metric(0L, null));
  }

  private long followsDuring(long ownerId, LocalDateTime fromAt, LocalDateTime toAt) {
    List<LiveSessionMapper.SessionWindow> rows = sessionMapper.listWindows(ownerId, fromAt, toAt);
    if (rows == null || rows.isEmpty()) {
      return 0L;
    }
    DateTimeFormatter ts = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    long sum = 0L;
    for (LiveSessionMapper.SessionWindow row : rows) {
      if (row == null || row.startedAt() == null) {
        continue;
      }
      LocalDateTime start = row.startedAt().isBefore(fromAt) ? fromAt : row.startedAt();
      LocalDateTime end = row.endedAt() == null || row.endedAt().isAfter(toAt) ? toAt : row.endedAt();
      if (!start.isBefore(end)) {
        continue;
      }
      try {
        UserClient.CountView view = userClient.countNewFollowers(internalToken, ownerId, ts.format(start), ts.format(end));
        sum += view == null ? 0L : view.count();
      } catch (RuntimeException ex) {
        log.warn("live follow count failed ownerId={}", ownerId, ex);
      }
    }
    return sum;
  }

  private static Window windowOf(String rangeRaw) {
    String range = rangeRaw == null ? "7" : rangeRaw.trim().toLowerCase(Locale.ROOT);
    int days;
    if ("1".equals(range)) {
      days = 1;
    } else if ("30".equals(range)) {
      days = 30;
    } else if ("7".equals(range)) {
      days = 7;
    } else {
      throw BusinessException.validation("range 只支持 1、7、30");
    }
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate start;
    LocalDate endExclusive;
    if (days == 1) {
      start = today.minusDays(1);
      endExclusive = today;
    } else {
      start = today.minusDays(days - 1L);
      endExclusive = today.plusDays(1);
    }
    LocalDateTime fromAt = start.atStartOfDay();
    LocalDateTime toAt = endExclusive.atStartOfDay();
    return new Window(range, fromAt, toAt, fromAt.minusDays(days), fromAt);
  }

  private static Double delta(long current, long previous) {
    if (previous == 0L) {
      return current == 0L ? null : 1.0d;
    }
    return (current - previous) / (double) previous;
  }

  private record Window(
      String range,
      LocalDateTime fromAt,
      LocalDateTime toAt,
      LocalDateTime prevFrom,
      LocalDateTime prevTo) {}

  private LiveHit findLiveByHook(String path, String streamKey) {
    String key = streamKeyFrom(path, streamKey);
    if (!StringUtils.hasText(key)) {
      return null;
    }
    return sessionMapper.findLiveByStreamKey(key);
  }

  private static String streamKeyFrom(String path, String streamKey) {
    if (StringUtils.hasText(streamKey)) {
      return streamKey.trim();
    }
    if (!StringUtils.hasText(path)) {
      return "";
    }
    String raw = path.trim();
    int query = raw.indexOf('?');
    if (query >= 0) {
      raw = raw.substring(0, query);
    }
    while (raw.endsWith("/")) {
      raw = raw.substring(0, raw.length() - 1);
    }
    int slash = raw.lastIndexOf('/');
    return slash >= 0 ? raw.substring(slash + 1) : raw;
  }

  private String normalizeNotice(String raw) {
    String text = raw == null ? "" : raw.trim();
    if (text.length() > NOTICE_MAX) {
      throw BusinessException.validation("公告最多 200 字");
    }
    policyCache.assertClean(text);
    return text;
  }
}
