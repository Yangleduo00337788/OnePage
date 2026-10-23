package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.content.client.AiClient;
import com.onepage.content.entity.Post;
import com.onepage.content.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 公开待审笔记机审。文字+图片取高分；低分自动上架，达阈值或失败留给人审，不自动驳回。 */
@Service
public class PostReviewService {
  private static final Logger log = LoggerFactory.getLogger(PostReviewService.class);
  private static final String STATUS_PENDING = "pending";
  private static final String VERDICT_PASS = "pass";
  private static final String VERDICT_FLAG = "flag";
  private static final String VERDICT_SKIP = "skip";
  private static final int REASON_MAX = 200;

  private final PostMapper postMapper;
  private final PostService postService;
  private final AiClient aiClient;
  private final String internalToken;

  public PostReviewService(
      PostMapper postMapper,
      @Lazy PostService postService,
      AiClient aiClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.postMapper = postMapper;
    this.postService = postService;
    this.aiClient = aiClient;
    this.internalToken = internalToken;
  }

  @Async("postReviewExecutor")
  public void enqueue(long postId) {
    try {
      review(postId);
    } catch (RuntimeException ex) {
      log.warn("post ai review {} failed", postId, ex);
      mark(postId, VERDICT_SKIP, "模型暂时不可用", null);
    }
  }

  private void review(long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null || !STATUS_PENDING.equals(post.getStatus())) {
      return;
    }
    AiClient.ReviewResult result;
    try {
      result = aiClient.review(
          internalToken,
          new AiClient.ReviewSpec(
              String.valueOf(postId),
              blank(post.getTitle()),
              blank(post.getBody()),
              postService.listReviewImageUrls(postId)));
    } catch (RuntimeException ex) {
      log.warn("post ai review call {} failed", postId, ex);
      mark(postId, VERDICT_SKIP, "模型暂时不可用", null);
      return;
    }
    String verdict = result == null || !StringUtils.hasText(result.verdict())
        ? VERDICT_SKIP
        : result.verdict().trim().toLowerCase();
    String reason = result == null ? "" : blank(result.reason());
    Integer score = result == null ? null : result.score();
    if (VERDICT_PASS.equals(verdict)) {
      postService.opsApprove(postId);
      mark(postId, VERDICT_PASS, reason, score);
      return;
    }
    if (!VERDICT_FLAG.equals(verdict)) {
      verdict = VERDICT_SKIP;
      if (!StringUtils.hasText(reason)) {
        reason = "未开启机审或模型未返回";
      }
    } else if (!StringUtils.hasText(reason)) {
      reason = "需人工复核";
    }
    mark(postId, verdict, reason, score);
  }

  private void mark(long postId, String verdict, String reason, Integer score) {
    Post post = postMapper.selectById(postId);
    if (post == null) {
      return;
    }
    String note = reason == null ? "" : reason.trim();
    if (note.length() > REASON_MAX) {
      note = note.substring(0, REASON_MAX);
    }
    postMapper.update(
        null,
        Wrappers.<Post>lambdaUpdate()
            .eq(Post::getId, postId)
            .set(Post::getAiReview, verdict)
            .set(Post::getAiReviewReason, note)
            .set(Post::getAiReviewScore, score)
            .set(Post::getUpdatedAt, LocalDateTime.now()));
  }

  private static String blank(String value) {
    return value == null ? "" : value;
  }
}
