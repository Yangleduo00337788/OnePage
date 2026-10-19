package com.onepage.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CreatorStatsMapper {
  @Select("""
      SELECT COUNT(*) FROM post_view v
      INNER JOIN post p ON p.id = v.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND v.viewed_at >= #{fromAt} AND v.viewed_at < #{toAt}
      """)
  long countViews(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM post_like l
      INNER JOIN post p ON p.id = l.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND l.created_at >= #{fromAt} AND l.created_at < #{toAt}
      """)
  long countLikes(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM comment c
      INNER JOIN post p ON p.id = c.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND c.status = 0
        AND c.created_at >= #{fromAt} AND c.created_at < #{toAt}
      """)
  long countComments(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM post_favorite f
      INNER JOIN post p ON p.id = f.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND f.created_at >= #{fromAt} AND f.created_at < #{toAt}
      """)
  long countFavorites(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT p.post_type AS postType, COUNT(*) AS cnt
      FROM post_view v
      INNER JOIN post p ON p.id = v.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND v.viewed_at >= #{fromAt} AND v.viewed_at < #{toAt}
      GROUP BY p.post_type
      """)
  List<TypeCount> mixByPostType(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT DATE_FORMAT(v.viewed_at, '%Y-%m-%d') AS statDay, COUNT(*) AS cnt
      FROM post_view v
      INNER JOIN post p ON p.id = v.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND v.viewed_at >= #{fromAt} AND v.viewed_at < #{toAt}
      GROUP BY DATE_FORMAT(v.viewed_at, '%Y-%m-%d')
      """)
  List<DayCount> viewsByDay(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT p.id AS postId, IFNULL(p.title, '') AS title, p.post_type AS postType,
             IFNULL(v.cnt, 0) AS views, IFNULL(l.cnt, 0) AS likes,
             IFNULL(c.cnt, 0) AS comments, IFNULL(f.cnt, 0) AS favorites
      FROM post p
      LEFT JOIN (
        SELECT post_id, COUNT(*) AS cnt FROM post_view
        WHERE viewed_at >= #{fromAt} AND viewed_at < #{toAt}
        GROUP BY post_id
      ) v ON v.post_id = p.id
      LEFT JOIN (
        SELECT post_id, COUNT(*) AS cnt FROM post_like
        WHERE created_at >= #{fromAt} AND created_at < #{toAt}
        GROUP BY post_id
      ) l ON l.post_id = p.id
      LEFT JOIN (
        SELECT post_id, COUNT(*) AS cnt FROM comment
        WHERE status = 0 AND created_at >= #{fromAt} AND created_at < #{toAt}
        GROUP BY post_id
      ) c ON c.post_id = p.id
      LEFT JOIN (
        SELECT post_id, COUNT(*) AS cnt FROM post_favorite
        WHERE created_at >= #{fromAt} AND created_at < #{toAt}
        GROUP BY post_id
      ) f ON f.post_id = p.id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND p.status = 'published'
      ORDER BY views DESC, p.id DESC
      LIMIT 200
      """)
  List<PostHit> listPosts(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT DISTINCT v.user_id
      FROM post_view v
      INNER JOIN post p ON p.id = v.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND v.user_id IS NOT NULL
        AND v.viewed_at >= #{fromAt} AND v.viewed_at < #{toAt}
      LIMIT 5000
      """)
  List<Long> listViewerIds(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT DISTINCT x.user_id FROM (
        SELECT l.user_id FROM post_like l
        INNER JOIN post p ON p.id = l.post_id
        WHERE p.author_id = #{authorId} AND p.deleted_at IS NULL
          AND l.created_at >= #{fromAt} AND l.created_at < #{toAt}
        UNION
        SELECT f.user_id FROM post_favorite f
        INNER JOIN post p2 ON p2.id = f.post_id
        WHERE p2.author_id = #{authorId} AND p2.deleted_at IS NULL
          AND f.created_at >= #{fromAt} AND f.created_at < #{toAt}
        UNION
        SELECT c.user_id FROM comment c
        INNER JOIN post p3 ON p3.id = c.post_id
        WHERE p3.author_id = #{authorId} AND p3.deleted_at IS NULL
          AND c.status = 0
          AND c.created_at >= #{fromAt} AND c.created_at < #{toAt}
      ) x
      LIMIT 5000
      """)
  List<Long> listEngagedIds(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM share_log s
      INNER JOIN post p ON p.id = s.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND s.created_at >= #{fromAt} AND s.created_at < #{toAt}
      """)
  long countShares(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM post_cover_show s
      INNER JOIN post p ON p.id = s.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND s.created_at >= #{fromAt} AND s.created_at < #{toAt}
      """)
  long countCoverShows(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM post_play_complete c
      INNER JOIN post p ON p.id = c.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND p.post_type = 'video'
        AND c.created_at >= #{fromAt} AND c.created_at < #{toAt}
      """)
  long countPlayCompletes(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM post_view v
      INNER JOIN post p ON p.id = v.post_id
      WHERE p.author_id = #{authorId}
        AND p.deleted_at IS NULL
        AND p.post_type = 'video'
        AND v.viewed_at >= #{fromAt} AND v.viewed_at < #{toAt}
      """)
  long countVideoViews(
      @Param("authorId") long authorId,
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt);

  @Select("""
      SELECT COUNT(*) FROM post
      WHERE author_id = #{authorId} AND deleted_at IS NULL AND status = #{status}
      """)
  long countByStatus(@Param("authorId") long authorId, @Param("status") String status);

  record TypeCount(String postType, long cnt) {}

  record DayCount(String statDay, long cnt) {}

  record PostHit(
      long postId, String title, String postType, long views, long likes, long comments, long favorites) {}
}
