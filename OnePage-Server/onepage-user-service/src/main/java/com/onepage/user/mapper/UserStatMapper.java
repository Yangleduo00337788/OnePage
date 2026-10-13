package com.onepage.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.user.entity.UserStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 计数表访问。 */
@Mapper
public interface UserStatMapper extends BaseMapper<UserStat> {
  @Update("""
      UPDATE user_stat
      SET following_count = GREATEST(0, following_count + #{delta}),
          updated_at = #{now}
      WHERE user_id = #{userId}
      """)
  int bumpFollowing(@Param("userId") long userId, @Param("delta") int delta, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE user_stat
      SET follower_count = GREATEST(0, follower_count + #{delta}),
          updated_at = #{now}
      WHERE user_id = #{userId}
      """)
  int bumpFollower(@Param("userId") long userId, @Param("delta") int delta, @Param("now") LocalDateTime now);
}
