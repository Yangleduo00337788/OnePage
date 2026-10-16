package com.onepage.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

  public List<SearchDtos.PostBrief> searchPosts(String q, String postType, int from, int size) {
    try {
      SearchResponse<SearchDtos.PostBrief> res = client.search(s -> s
              .index(POSTS)
              .from(from)
              .size(size)
              .query(postQuery(q, postType)),
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

  private static Query postQuery(String q, String postType) {
    Query text = Query.of(b -> b.multiMatch(m -> m
        .query(q)
        .fields("title^3", "body", "author.nickname^2", "author.pageId^2")));
    if (!StringUtils.hasText(postType)) {
      return text;
    }
    return Query.of(b -> b.bool(bool -> bool
        .must(text)
        .filter(f -> f.match(m -> m.field("postType").query(postType.trim())))));
  }

  private static Query userQuery(String q) {
    return Query.of(b -> b.multiMatch(m -> m
        .query(q)
        .fields("nickname^3", "pageId^3", "bio")));
  }
}
