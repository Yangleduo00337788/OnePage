package com.onepage.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.content.dto.CountHit;
import com.onepage.content.entity.Comment;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT post_id AS postId, COUNT(*) AS cnt
      FROM comment
      WHERE status = 0
        AND post_id IN
      <foreach item="id" collection="postIds" open="(" separator="," close=")">
        #{id}
      </foreach>
      GROUP BY post_id
      </script>
      """)
  List<CountHit> countVisibleByPostIds(@Param("postIds") List<Long> postIds);
}
