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
}
