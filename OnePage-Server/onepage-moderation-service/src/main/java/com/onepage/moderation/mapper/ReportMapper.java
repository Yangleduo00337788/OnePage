package com.onepage.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.moderation.entity.Report;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {
  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT id, reporter_id, target_type, target_id, reason_code, detail, status,
             handler_id, handle_action, handle_note, handled_at, created_at
      FROM report
      WHERE 1 = 1
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
  List<Report> page(
      @Param("status") Integer status,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);
}
