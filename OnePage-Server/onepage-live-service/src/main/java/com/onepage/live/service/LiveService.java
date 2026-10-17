package com.onepage.live.service;

import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 房间与场次。同一主播同时只能一场 live。 */
@Service
public class LiveService {
  private static final Logger log = LoggerFactory.getLogger(LiveService.class);
  private final LiveRoomMapper roomMapper;
  private final LiveSessionMapper sessionMapper;
  private final UserClient userClient;
  private final StringRedisTemplate redis;
  private final String internalToken;
  private final String pushBase;
  private final String hlsBase;

  public LiveService(
      LiveRoomMapper roomMapper,
      LiveSessionMapper sessionMapper,
      UserClient userClient,
      StringRedisTemplate redis,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.live-push-base}") String pushBase,
      @Value("${onepage.live-hls-base}") String hlsBase) {
    this.roomMapper = roomMapper;
    this.sessionMapper = sessionMapper;
    this.userClient = userClient;
    this.redis = redis;
    this.internalToken = internalToken;
    this.pushBase = pushBase;
    this.hlsBase = hlsBase;
  }

  public LiveDtos.RoomCreated createRoom(long ownerId, LiveDtos.RoomSpec spec) {
    String title = spec == null || spec.title() == null ? "" : spec.title().trim();
    if (title.isEmpty() || title.length() > 100) {
      throw BusinessException.validation("请填写直播标题");
    }
    LiveRoom room = new LiveRoom();
    room.setId(IdGenerator.nextId());
    room.setOwnerId(ownerId);
    room.setTitle(title);
    room.setCategory(spec.category() == null ? "" : spec.category().trim());
    room.setCoverObjectKey("");
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
      return toStart(existing);
    }
    LiveSession session = new LiveSession();
    session.setId(IdGenerator.nextId());
    session.setRoomId(roomId);
    session.setStreamKey(UUID.randomUUID().toString().replace("-", ""));
    session.setStatus("live");
    session.setStartedAt(LocalDateTime.now());
    sessionMapper.insert(session);
    redis.opsForValue().set(viewerKey(session.getId()), "0");
    LiveHit hit = sessionMapper.findHit(session.getId());
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
    LiveSession session = sessionMapper.selectById(live.getSessionId());
    if (session == null) {
      return;
    }
    session.setStatus("ended");
    session.setEndedAt(LocalDateTime.now());
    sessionMapper.updateById(session);
  }

  public LiveDtos.LivePage list(String q, String category) {
    String cat = category == null || "全部".equals(category.trim()) ? "" : category.trim();
    String keyword = q == null ? "" : q.trim();
    List<LiveHit> rows = sessionMapper.listLive(keyword, cat);
    return new LiveDtos.LivePage(toItems(rows, false));
  }

  public LiveDtos.LiveItem get(long sessionId, boolean peek) {
    LiveHit hit = sessionMapper.findHit(sessionId);
    if (hit == null) {
      throw BusinessException.notFound("场次不存在");
    }
    boolean live = "live".equals(hit.getStatus());
    boolean countViewer = live && !peek;
    if (countViewer) {
      redis.opsForValue().increment(viewerKey(sessionId));
    }
    List<LiveDtos.LiveItem> items = toItems(List.of(hit), countViewer);
    return items.getFirst();
  }

  private LiveDtos.LiveStart toStart(LiveHit hit) {
    String key = hit.getStreamKey();
    return new LiveDtos.LiveStart(
        String.valueOf(hit.getSessionId()),
        String.valueOf(hit.getRoomId()),
        nullToEmpty(hit.getTitle()),
        trimSlash(pushBase) + "/" + key,
        hlsPlayUrl(key),
        key);
  }

  private List<LiveDtos.LiveItem> toItems(List<LiveHit> rows, boolean afterIncr) {
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
          row.getOwnerId(), new UserSummary(String.valueOf(row.getOwnerId()), "", "主播", ""));
      String started = row.getStartedAt() == null
          ? ""
          : row.getStartedAt().toInstant(ZoneOffset.UTC).toString();
      out.add(new LiveDtos.LiveItem(
          String.valueOf(row.getSessionId()),
          String.valueOf(row.getRoomId()),
          nullToEmpty(row.getTitle()),
          nullToEmpty(row.getCategory()),
          nullToEmpty(row.getStatus()),
          nullToEmpty(row.getCoverObjectKey()),
          hlsPlayUrl(row.getStreamKey()),
          viewers(row.getSessionId(), afterIncr),
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

  private long viewers(long sessionId, boolean afterIncr) {
    String raw = redis.opsForValue().get(viewerKey(sessionId));
    if (!StringUtils.hasText(raw)) {
      return afterIncr ? 1L : 0L;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }

  private static boolean ownerIdEquals(Long ownerId, long userId) {
    return ownerId != null && ownerId == userId;
  }

  private static String viewerKey(long sessionId) {
    return "live:viewers:" + sessionId;
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
}
