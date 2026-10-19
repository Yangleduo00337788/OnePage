package com.onepage.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface UserUnfollowLogMapper {
  @Insert("""
      INSERT INTO user_unfollow_log (id, follower_id, followee_id, followed_at, unfollowed_at)
      VALUES (#{id}, #{followerId}, #{followeeId}, #{followedAt}, #{unfollowedAt})
      """)
  int insert(
      @Param("id") long id,
      @Param("followerId") long followerId,
      @Param("followeeId") long followeeId,
      @Param("followedAt") LocalDateTime followedAt,
      @Param("unfollowedAt") LocalDateTime unfollowedAt);

  @Select("""
      SELECT COUNT(*) FROM user_unfollow_log
      WHERE followee_id = #{ownerId}
        AND unfollowed_at >= #{fromAt} AND unfollowed_at < #{toAt}
      """)
  long countInRange(
      @Param("ownerId") long ownerId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(DISTINCT follower_id) FROM user_unfollow_log
      WHERE followee_id = #{ownerId}
        AND followed_at >= #{cohortFrom} AND followed_at < #{cohortTo}
        AND unfollowed_at < #{asOf}
      """)
  long countLeftCohort(
      @Param("ownerId") long ownerId,
      @Param("cohortFrom") LocalDateTime cohortFrom,
      @Param("cohortTo") LocalDateTime cohortTo,
      @Param("asOf") LocalDateTime asOf);
}
