package com.onepage.user.mapper;

import com.onepage.user.dto.FollowHit;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserFollowMapper {
  @Insert("""
      INSERT IGNORE INTO user_follow (follower_id, followee_id, created_at)
      VALUES (#{followerId}, #{followeeId}, #{createdAt})
      """)
  int insertIgnore(
      @Param("followerId") long followerId,
      @Param("followeeId") long followeeId,
      @Param("createdAt") LocalDateTime createdAt);

  @Delete("DELETE FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
  int deletePair(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

  @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
  int existsPair(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

  @Select("SELECT followee_id FROM user_follow WHERE follower_id = #{userId}")
  List<Long> listFolloweeIds(@Param("userId") long userId);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT f.followee_id AS userId, f.created_at AS at
      FROM user_follow f
      WHERE f.follower_id = #{ownerId}
        <if test="cursorAt != null">
          AND (f.created_at &lt; #{cursorAt}
            OR (f.created_at = #{cursorAt} AND f.followee_id &lt; #{cursorUserId}))
        </if>
      ORDER BY f.created_at DESC, f.followee_id DESC
      LIMIT #{limit}
      </script>
      """)
  List<FollowHit> pageFollowing(
      @Param("ownerId") long ownerId,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorUserId") Long cursorUserId,
      @Param("limit") int limit);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT f.follower_id AS userId, f.created_at AS at
      FROM user_follow f
      WHERE f.followee_id = #{ownerId}
        <if test="cursorAt != null">
          AND (f.created_at &lt; #{cursorAt}
            OR (f.created_at = #{cursorAt} AND f.follower_id &lt; #{cursorUserId}))
        </if>
      ORDER BY f.created_at DESC, f.follower_id DESC
      LIMIT #{limit}
      </script>
      """)
  List<FollowHit> pageFollowers(
      @Param("ownerId") long ownerId,
      @Param("cursorAt") LocalDateTime cursorAt,
      @Param("cursorUserId") Long cursorUserId,
      @Param("limit") int limit);

  @Select("SELECT COUNT(*) FROM user_follow WHERE followee_id = #{ownerId}")
  long countFollowers(@Param("ownerId") long ownerId);

  @Select("""
      SELECT COUNT(*) FROM user_follow
      WHERE followee_id = #{ownerId}
        AND created_at >= #{fromAt} AND created_at < #{toAt}
      """)
  long countNewFollowers(
      @Param("ownerId") long ownerId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM user_follow a
      INNER JOIN user_follow b
        ON b.follower_id = #{ownerId} AND b.followee_id = a.follower_id
      WHERE a.followee_id = #{ownerId}
      """)
  long countReciprocal(@Param("ownerId") long ownerId);

  @Select("""
      SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS statDay, COUNT(*) AS cnt
      FROM user_follow
      WHERE followee_id = #{ownerId}
        AND created_at >= #{fromAt} AND created_at < #{toAt}
      GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')
      """)
  List<DayCount> countNewByDay(
      @Param("ownerId") long ownerId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Lang(XMLLanguageDriver.class)
  @Select("""
      <script>
      SELECT COUNT(*) FROM user_follow
      WHERE followee_id = #{ownerId}
        AND follower_id IN
        <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
      </script>
      """)
  long countFollowersIn(
      @Param("ownerId") long ownerId,
      @Param("ids") List<Long> ids);

  record DayCount(String statDay, long cnt) {}
}
