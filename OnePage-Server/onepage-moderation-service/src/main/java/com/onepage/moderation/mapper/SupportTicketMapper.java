package com.onepage.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.moderation.entity.SupportTicket;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupportTicketMapper extends BaseMapper<SupportTicket> {
  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT id, user_id, kind, title, contact, body, media_urls, status,
             handler_id, handle_note, handled_at, created_at
      FROM support_ticket
      WHERE 1 = 1
        <if test="kind != null">
          AND kind = #{kind}
        </if>
        <if test="status != null">
          AND status = #{status}
        </if>
        <if test="cursorAt != null">
          AND (created_at &lt; #{cursorAt}
            OR (created_at = #{cursorAt} AND id &lt; #{cursorId}))
        </if>
      ORDER BY created_at DESC, id DESC
      LIMIT #{limit}
      </script>
      """)
  List<SupportTicket> page(
      @Param("kind") String kind,
      @Param("status") Integer status,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);
}
