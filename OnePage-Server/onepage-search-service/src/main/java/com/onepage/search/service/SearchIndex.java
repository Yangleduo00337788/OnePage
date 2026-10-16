package com.onepage.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.onepage.search.dto.SearchDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** ES 索引名与读写。公开笔记 {@code onepage-posts}，用户 {@code onepage-users}。 */
@Component
public class SearchIndex {
  static final String POSTS = "onepage-posts";
  static final String USERS = "onepage-users";
  private static final Logger log = LoggerFactory.getLogger(SearchIndex.class);

  private final ElasticsearchClient client;

  public SearchIndex(ElasticsearchClient client) {
    this.client = client;
  }

  public void ensureIndices() {
    try {
      if (!client.indices().exists(ExistsRequest.of(e -> e.index(POSTS))).value()) {
        client.indices().create(c -> c.index(POSTS));
      }
      if (!client.indices().exists(ExistsRequest.of(e -> e.index(USERS))).value()) {
        client.indices().create(c -> c.index(USERS));
      }
    } catch (IOException ex) {
      log.warn("ensure ES indices failed", ex);
    }
  }

  public void upsertPost(SearchDtos.PostBrief doc) {
    if (doc == null || !StringUtils.hasText(doc.postId())) {
      return;
    }
    try {
      client.index(i -> i.index(POSTS).id(doc.postId()).document(doc));
    } catch (IOException ex) {
      throw new IllegalStateException("index post failed", ex);
    }
  }

  public void deletePost(String postId) {
    if (!StringUtils.hasText(postId)) {
      return;
    }
    try {
      client.delete(d -> d.index(POSTS).id(postId));
    } catch (IOException ex) {
      log.warn("delete post {} from ES failed", postId, ex);
    }
  }

  public void upsertUser(SearchDtos.UserDoc doc) {
    if (doc == null || !StringUtils.hasText(doc.userId())) {
      return;
    }
    try {
      client.index(i -> i.index(USERS).id(doc.userId()).document(doc));
    } catch (IOException ex) {
      throw new IllegalStateException("index user failed", ex);
    }
  }

  public List<SearchDtos.PostBrief> searchPosts(SearchDtos.PostFilter filter) {
    try {
      SearchResponse<SearchDtos.PostBrief> res = client.search(s -> {
            s.index(POSTS).from(filter.from()).size(filter.size()).query(postQuery(filter));
            String sortField = sortField(filter.sort());
            if (sortField != null) {
              s.sort(so -> so.field(f -> f.field(sortField).order(SortOrder.Desc).unmappedType(FieldType.Long)));
            }
            return s;
          },
          SearchDtos.PostBrief.class);
      List<SearchDtos.PostBrief> out = new ArrayList<>();
      for (Hit<SearchDtos.PostBrief> hit : res.hits().hits()) {
        if (hit.source() != null) {
          out.add(hit.source());
        }
      }
      return out;
    } catch (IOException ex) {
      throw new IllegalStateException("search posts failed", ex);
    }
  }

  public List<SearchDtos.UserDoc> searchUsers(String q, int from, int size) {
    try {
      SearchResponse<SearchDtos.UserDoc> res = client.search(s -> s
              .index(USERS)
              .from(from)
              .size(size)
              .query(userQuery(q)),
          SearchDtos.UserDoc.class);
      List<SearchDtos.UserDoc> out = new ArrayList<>();
      for (Hit<SearchDtos.UserDoc> hit : res.hits().hits()) {
        if (hit.source() != null) {
          out.add(hit.source());
        }
      }
      return out;
    } catch (IOException ex) {
      throw new IllegalStateException("search users failed", ex);
    }
  }

  private static Query postQuery(SearchDtos.PostFilter filter) {
    Query text = Query.of(b -> b.multiMatch(m -> m
        .query(filter.q())
        .fields("title^3", "body", "author.nickname^2", "author.pageId^2", "category")));
    return Query.of(b -> b.bool(bool -> {
      bool.must(text);
      List<String> types = splitCsv(filter.postType());
      if (!types.isEmpty()) {
        bool.filter(f -> f.terms(t -> t.field("postType").terms(v -> v.value(toValues(types)))));
      }
      String since = sinceIso(filter.time());
      if (since != null) {
        bool.filter(f -> f.range(r -> r.date(d -> d.field("publishedAt").gte(since))));
      }
      if ("following".equals(filter.scope()) && filter.authorIds() != null && !filter.authorIds().isEmpty()) {
        bool.filter(f -> f.terms(t -> t.field("author.userId").terms(v -> v.value(toValues(filter.authorIds())))));
      }
      if (("seen".equals(filter.scope()) || "unseen".equals(filter.scope()))
          && filter.seenIds() != null
          && !filter.seenIds().isEmpty()) {
        Query ids = Query.of(q -> q.ids(i -> i.values(filter.seenIds())));
        if ("seen".equals(filter.scope())) {
          bool.filter(ids);
        } else {
          bool.mustNot(ids);
        }
      }
      return bool;
    }));
  }

  private static String sortField(String sort) {
    if (sort == null) {
      return null;
    }
    return switch (sort.trim()) {
      case "latest" -> "publishedAt";
      case "hot" -> "viewCount";
      case "likes" -> "likeCount";
      case "comments" -> "commentCount";
      case "favorites" -> "favoriteCount";
      default -> null;
    };
  }

  private static String sinceIso(String time) {
    if (time == null || time.isBlank() || "any".equals(time)) {
      return null;
    }
    Instant now = Instant.now();
    Instant from = switch (time.trim()) {
      case "week" -> now.minus(7, ChronoUnit.DAYS);
      case "month" -> now.minus(30, ChronoUnit.DAYS);
      case "half" -> now.minus(180, ChronoUnit.DAYS);
      default -> null;
    };
    return from == null ? null : from.toString();
  }

  private static List<String> splitCsv(String raw) {
    if (!StringUtils.hasText(raw)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String part : raw.split(",")) {
      String t = part.trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out;
  }

  private static List<FieldValue> toValues(List<String> raw) {
    List<FieldValue> out = new ArrayList<>();
    for (String item : raw) {
      out.add(FieldValue.of(item));
    }
    return out;
  }

  private static Query userQuery(String q) {
    return Query.of(b -> b.multiMatch(m -> m
        .query(q)
        .fields("nickname^3", "pageId^3", "bio")));
  }
}
