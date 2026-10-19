package com.onepage.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.content.entity.Topic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TopicMapper extends BaseMapper<Topic> {
  @Select("""
      SELECT t.id AS topicId, COUNT(p.id) AS cnt
      FROM topic t
      LEFT JOIN post p
        ON p.deleted_at IS NULL
       AND p.status = 'published'
       AND p.body LIKE CONCAT('%#', t.name, '%')
      GROUP BY t.id
      """)
  List<CountRow> countPostsByTopic();

  record CountRow(long topicId, long cnt) {}
}
