package com.onepage.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.content.dto.CoverHit;
import com.onepage.content.entity.PostMedia;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.util.List;

@Mapper
public interface PostMediaMapper extends BaseMapper<PostMedia> {
  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT pm.post_id AS postId, pm.role AS role, pm.sort_order AS sortOrder,
             ma.object_key AS objectKey, ma.mime_type AS mimeType
      FROM post_media pm
      INNER JOIN media_asset ma ON ma.id = pm.media_id
      WHERE ma.blocked_at IS NULL
        AND ma.mime_type LIKE 'image/%'
        AND pm.post_id IN
      <foreach item="id" collection="postIds" open="(" separator="," close=")">
        #{id}
      </foreach>
      ORDER BY pm.post_id ASC,
               CASE WHEN pm.role = 'cover' THEN 0 ELSE 1 END ASC,
               pm.sort_order ASC
      </script>
      """)
  List<CoverHit> listImageCovers(@Param("postIds") List<Long> postIds);
}
