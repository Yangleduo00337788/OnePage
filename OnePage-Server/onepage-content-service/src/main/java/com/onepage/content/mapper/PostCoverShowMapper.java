package com.onepage.content.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostCoverShowMapper {
  @Lang(XMLLanguageDriver.class)
  @Insert("""
      <script>
      INSERT INTO post_cover_show (id, viewer_id, post_id, created_at) VALUES
      <foreach item="row" collection="rows" separator=",">
        (#{row.id}, #{row.viewerId}, #{row.postId}, #{row.createdAt})
      </foreach>
      </script>
      """)
  void insertBatch(@Param("rows") List<ShowRow> rows);

  record ShowRow(long id, Long viewerId, long postId, LocalDateTime createdAt) {}
}
