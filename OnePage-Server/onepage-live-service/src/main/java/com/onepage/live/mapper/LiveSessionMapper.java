package com.onepage.live.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.live.dto.LiveHit;
import com.onepage.live.entity.LiveSession;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

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
}
