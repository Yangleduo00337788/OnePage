package com.onepage.live.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.live.dto.LiveHit;
import com.onepage.live.entity.LiveSession;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LiveSessionMapper extends BaseMapper<LiveSession> {
  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT s.id AS sessionId, s.room_id AS roomId, r.owner_id AS ownerId,
             r.title AS title, r.category AS category, r.cover_object_key AS coverObjectKey,
             r.notice AS notice,
             s.stream_key AS streamKey, s.status AS status, s.started_at AS startedAt
      FROM live_session s
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE s.status = 'live'
        <if test="q != null and q != ''">
          AND r.title LIKE CONCAT('%', #{q}, '%')
        </if>
        <if test="category != null and category != ''">
          AND r.category = #{category}
        </if>
      ORDER BY s.started_at DESC
      LIMIT 50
      </script>
      """)
  List<LiveHit> listLive(@Param("q") String q, @Param("category") String category);

  @Select("""
      SELECT s.id AS sessionId, s.room_id AS roomId, r.owner_id AS ownerId,
             r.title AS title, r.category AS category, r.cover_object_key AS coverObjectKey,
             r.notice AS notice,
             s.stream_key AS streamKey, s.status AS status, s.started_at AS startedAt
      FROM live_session s
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE s.id = #{sessionId}
      LIMIT 1
      """)
  LiveHit findHit(@Param("sessionId") long sessionId);

  @Select("""
      SELECT s.id AS sessionId, s.room_id AS roomId, r.owner_id AS ownerId,
             r.title AS title, r.category AS category, r.cover_object_key AS coverObjectKey,
             r.notice AS notice,
             s.stream_key AS streamKey, s.status AS status, s.started_at AS startedAt
      FROM live_session s
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE r.owner_id = #{ownerId} AND s.status = 'live'
      LIMIT 1
      """)
  LiveHit findLiveByOwner(@Param("ownerId") long ownerId);

  @Select("""
      SELECT s.id AS sessionId, s.room_id AS roomId, r.owner_id AS ownerId,
             r.title AS title, r.category AS category, r.cover_object_key AS coverObjectKey,
             r.notice AS notice,
             s.stream_key AS streamKey, s.status AS status, s.started_at AS startedAt
      FROM live_session s
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE s.stream_key = #{streamKey} AND s.status = 'live'
      LIMIT 1
      """)
  LiveHit findLiveByStreamKey(@Param("streamKey") String streamKey);

  @Select("""
      SELECT COUNT(*) FROM live_session s
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE r.owner_id = #{ownerId}
        AND s.started_at IS NOT NULL
        AND s.started_at >= #{fromAt} AND s.started_at < #{toAt}
        AND s.status IN ('live', 'ended')
      """)
  long countSessions(
      @Param("ownerId") long ownerId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(DISTINCT c.user_id) FROM live_chat c
      INNER JOIN live_session s ON s.id = c.session_id
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE r.owner_id = #{ownerId}
        AND c.msg_type = 'join'
        AND s.started_at IS NOT NULL
        AND s.started_at >= #{fromAt} AND s.started_at < #{toAt}
      """)
  long countJoiners(
      @Param("ownerId") long ownerId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT IFNULL(MAX(s.peak_viewer_count), 0) FROM live_session s
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE r.owner_id = #{ownerId}
        AND s.started_at IS NOT NULL
        AND s.started_at >= #{fromAt} AND s.started_at < #{toAt}
        AND s.status IN ('live', 'ended')
      """)
  long maxPeak(
      @Param("ownerId") long ownerId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT s.started_at AS startedAt, IFNULL(s.ended_at, NOW(3)) AS endedAt
      FROM live_session s
      INNER JOIN live_room r ON r.id = s.room_id
      WHERE r.owner_id = #{ownerId}
        AND s.started_at IS NOT NULL
        AND s.started_at < #{toAt}
        AND (s.ended_at IS NULL OR s.ended_at >= #{fromAt})
        AND s.status IN ('live', 'ended')
      ORDER BY s.started_at ASC
      LIMIT 60
      """)
  List<SessionWindow> listWindows(
      @Param("ownerId") long ownerId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Update("""
      UPDATE live_session
      SET peak_viewer_count = GREATEST(IFNULL(peak_viewer_count, 0), #{count})
      WHERE id = #{sessionId}
      """)
  void bumpPeak(@Param("sessionId") long sessionId, @Param("count") long count);

  record SessionWindow(LocalDateTime startedAt, LocalDateTime endedAt) {}
}
