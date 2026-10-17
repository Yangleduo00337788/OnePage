package com.onepage.live.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.live.entity.LiveChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LiveChatMapper extends BaseMapper<LiveChat> {
  @Select("""
      SELECT id, session_id, user_id, body, msg_type, created_at
      FROM live_chat
      WHERE session_id = #{sessionId}
      ORDER BY id DESC
      LIMIT #{limit}
      """)
  List<LiveChat> latest(@Param("sessionId") long sessionId, @Param("limit") int limit);

  @Select("""
      SELECT id, session_id, user_id, body, msg_type, created_at
      FROM live_chat
      WHERE session_id = #{sessionId} AND id > #{afterId}
      ORDER BY id ASC
      LIMIT #{limit}
      """)
  List<LiveChat> after(
      @Param("sessionId") long sessionId, @Param("afterId") long afterId, @Param("limit") int limit);
}
