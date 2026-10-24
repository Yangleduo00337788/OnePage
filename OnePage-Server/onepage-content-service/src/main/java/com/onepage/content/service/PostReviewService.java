package com.onepage.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onepage.content.client.AiClient;
import com.onepage.content.entity.MediaAsset;
import com.onepage.content.entity.Post;
import com.onepage.content.entity.PostMedia;
import com.onepage.content.mapper.MediaAssetMapper;
import com.onepage.content.mapper.PostMapper;
import com.onepage.content.mapper.PostMediaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 公开待审笔记机审。图在 complete 后先审；发帖汇总文字+媒体。
 * 高置信自动驳回，灰区人审，低风险上架；失败或视频未抽帧进人审。
 */
@Service
public class PostReviewService {
  private static final Logger log = LoggerFactory.getLogger(PostReviewService.class);
  private static final String STATUS_PENDING = "pending";
  private static final String VERDICT_PASS = "pass";
  private static final String VERDICT_FLAG = "flag";
  private static final String VERDICT_REJECT = "reject";
  private static final String VERDICT_SKIP = "skip";
  private static final String PREFIX_IMAGE = "post_image/";
  private static final String PREFIX_VIDEO = "post_video/";
  private static final int REASON_MAX = 200;
  private static final int LABELS_MAX = 64;
  private static final int SAMPLE_EVERY = 20;
  private static final long SYSTEM_ACTOR = 0L;

  private final PostMapper postMapper;
  private final PostMediaMapper postMediaMapper;
  private final MediaAssetMapper mediaAssetMapper;
  private final PostService postService;
  private final AiClient aiClient;
  private final String internalToken;
  private final String mediaPublicBase;

  public PostReviewService(
      PostMapper postMapper,
      PostMediaMapper postMediaMapper,
      MediaAssetMapper mediaAssetMapper,
      @Lazy PostService postService,
      AiClient aiClient,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.media-public-base}") String mediaPublicBase) {
    this.postMapper = postMapper;
    this.postMediaMapper = postMediaMapper;
    this.mediaAssetMapper = mediaAssetMapper;
    this.postService = postService;
    this.aiClient = aiClient;
    this.internalToken = internalToken;
    this.mediaPublicBase = mediaPublicBase == null ? "" : mediaPublicBase.replaceAll("/+$", "");
  }

  @Async("postReviewExecutor")
  public void enqueue(long postId) {
    try {
      review(postId);
    } catch (RuntimeException ex) {
      log.warn("post ai review {} failed", postId, ex);
      mark(postId, VERDICT_SKIP, "模型暂时不可用", null, "");
    }
  }

  @Async("postReviewExecutor")
  public void enqueueMedia(long mediaId) {
    try {
      reviewMedia(mediaId);
    } catch (RuntimeException ex) {
      log.warn("media ai review {} failed", mediaId, ex);
      markMedia(mediaId, VERDICT_SKIP, "模型暂时不可用", null, "");
    }
  }

  private void review(long postId) {
    Post post = postMapper.selectById(postId);
    if (post == null || post.getDeletedAt() != null || !STATUS_PENDING.equals(post.getStatus())) {
      return;
    }
    List<Hit> hits = new ArrayList<>(reviewPostMedia(postId));
    boolean hasText = StringUtils.hasText(post.getTitle()) || StringUtils.hasText(post.getBody());
    if (hasText) {
      hits.add(reviewText(postId, blank(post.getTitle()), blank(post.getBody())));
    }
    if (hits.isEmpty()) {
      mark(postId, VERDICT_SKIP, "无标题正文或图片", null, "");
      return;
    }
    Hit worst = pickWorst(hits);
    String labels = joinLabels(hits);
    if (VERDICT_REJECT.equals(worst.verdict)) {
      mark(postId, VERDICT_REJECT, worst.reason, worst.score, labels);
      postService.opsReject(postId, clip(worst.reason, "含违规内容"), SYSTEM_ACTOR);
      return;
    }
    if (VERDICT_PASS.equals(worst.verdict) && postId % SAMPLE_EVERY == 0L) {
      mark(postId, VERDICT_FLAG, "机审通过，抽检进人审", worst.score, labels);
      return;
    }
    if (VERDICT_PASS.equals(worst.verdict)) {
      postService.opsApprove(postId);
      mark(postId, VERDICT_PASS, worst.reason, worst.score, labels);
      return;
    }
    String reason = worst.reason;
    if (!StringUtils.hasText(reason)) {
      reason = VERDICT_SKIP.equals(worst.verdict) ? "未开启机审或模型未返回" : "需人工复核";
    }
    mark(postId, VERDICT_FLAG.equals(worst.verdict) ? VERDICT_FLAG : VERDICT_SKIP, reason, worst.score, labels);
  }

  private List<Hit> reviewPostMedia(long postId) {
    List<PostMedia> links = postMediaMapper.selectList(
        Wrappers.<PostMedia>lambdaQuery()
            .eq(PostMedia::getPostId, postId)
            .orderByAsc(PostMedia::getSortOrder));
    List<Hit> hits = new ArrayList<>();
    int images = 0;
    for (PostMedia link : links) {
      MediaAsset asset = mediaAssetMapper.selectById(link.getMediaId());
      if (asset == null || asset.getBlockedAt() != null) {
        continue;
      }
      String key = asset.getObjectKey() == null ? "" : asset.getObjectKey();
      if (key.startsWith(PREFIX_VIDEO)) {
        Hit hit = reviewMedia(asset.getId());
        if (hit != null) {
          hits.add(hit);
        }
        continue;
      }
      if (!key.startsWith(PREFIX_IMAGE)) {
        continue;
      }
      if (images >= 4) {
        break;
      }
      images++;
      Hit hit = reviewMedia(asset.getId());
      if (hit != null) {
        hits.add(hit);
      }
    }
    return hits;
  }

  private Hit reviewMedia(long mediaId) {
    MediaAsset asset = mediaAssetMapper.selectById(mediaId);
    if (asset == null || asset.getStatus() == null || asset.getStatus() != 1) {
      return null;
    }
    if (StringUtils.hasText(asset.getAiReview())) {
      return new Hit(asset.getAiReview(), blank(asset.getAiReviewReason()),
          asset.getAiReviewScore(), blank(asset.getAiReviewLabels()));
    }
    String key = asset.getObjectKey() == null ? "" : asset.getObjectKey();
    if (key.startsWith(PREFIX_VIDEO)) {
      markMedia(mediaId, VERDICT_SKIP, "视频抽帧未做", null, "");
      return new Hit(VERDICT_SKIP, "视频抽帧未做", null, "");
    }
    if (!key.startsWith(PREFIX_IMAGE)) {
      return null;
    }
    String url = publicUrl(key);
    if (!StringUtils.hasText(url)) {
      markMedia(mediaId, VERDICT_SKIP, "没有可审的图片地址", null, "");
      return new Hit(VERDICT_SKIP, "没有可审的图片地址", null, "");
    }
    AiClient.ReviewResult result;
    try {
      result = aiClient.review(
          internalToken,
          new AiClient.ReviewSpec("", "", "", List.of(url)));
    } catch (RuntimeException ex) {
      log.warn("media ai review call {} failed", mediaId, ex);
      markMedia(mediaId, VERDICT_SKIP, "模型暂时不可用", null, "");
      return new Hit(VERDICT_SKIP, "模型暂时不可用", null, "");
    }
    Hit hit = toHit(result);
    markMedia(mediaId, hit.verdict, hit.reason, hit.score, hit.labels);
    return hit;
  }

  private Hit reviewText(long postId, String title, String body) {
    try {
      return toHit(aiClient.review(
          internalToken,
          new AiClient.ReviewSpec(String.valueOf(postId), title, body, List.of())));
    } catch (RuntimeException ex) {
      log.warn("post text review {} failed", postId, ex);
      return new Hit(VERDICT_SKIP, "模型暂时不可用", null, "");
    }
  }

  private static Hit toHit(AiClient.ReviewResult result) {
    if (result == null || !StringUtils.hasText(result.verdict())) {
      return new Hit(VERDICT_SKIP, "未开启机审或模型未返回", null, "");
    }
    String verdict = result.verdict().trim().toLowerCase();
    if (!VERDICT_PASS.equals(verdict) && !VERDICT_FLAG.equals(verdict)
        && !VERDICT_REJECT.equals(verdict)) {
      verdict = VERDICT_SKIP;
    }
    return new Hit(verdict, blank(result.reason()), result.score(), join(result.labels()));
  }

  private static Hit pickWorst(List<Hit> hits) {
    Hit worst = hits.getFirst();
    for (Hit hit : hits) {
      if (rank(hit.verdict) > rank(worst.verdict)) {
        worst = hit;
        continue;
      }
      if (rank(hit.verdict) == rank(worst.verdict) && scoreOf(hit) > scoreOf(worst)) {
        worst = hit;
      }
    }
    return worst;
  }

  private static int rank(String verdict) {
    if (VERDICT_REJECT.equals(verdict)) {
      return 4;
    }
    if (VERDICT_FLAG.equals(verdict)) {
      return 3;
    }
    if (VERDICT_SKIP.equals(verdict)) {
      return 2;
    }
    if (VERDICT_PASS.equals(verdict)) {
      return 1;
    }
    return 0;
  }

  private static int scoreOf(Hit hit) {
    return hit.score == null ? -1 : hit.score;
  }

  private void mark(long postId, String verdict, String reason, Integer score, String labels) {
    postMapper.update(
        null,
        Wrappers.<Post>lambdaUpdate()
            .eq(Post::getId, postId)
            .set(Post::getAiReview, verdict)
            .set(Post::getAiReviewReason, clip(reason, ""))
            .set(Post::getAiReviewScore, score)
            .set(Post::getAiReviewLabels, clip(labels, "", LABELS_MAX))
            .set(Post::getUpdatedAt, LocalDateTime.now()));
  }

  private void markMedia(long mediaId, String verdict, String reason, Integer score, String labels) {
    mediaAssetMapper.update(
        null,
        Wrappers.<MediaAsset>lambdaUpdate()
            .eq(MediaAsset::getId, mediaId)
            .set(MediaAsset::getAiReview, verdict)
            .set(MediaAsset::getAiReviewReason, clip(reason, ""))
            .set(MediaAsset::getAiReviewScore, score)
            .set(MediaAsset::getAiReviewLabels, clip(labels, "", LABELS_MAX)));
  }

  private String publicUrl(String objectKey) {
    if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(mediaPublicBase)) {
      return "";
    }
    return mediaPublicBase + "/" + objectKey;
  }

  private static String joinLabels(List<Hit> hits) {
    Set<String> labels = new LinkedHashSet<>();
    for (Hit hit : hits) {
      for (String item : hit.labels.split(",")) {
        if (StringUtils.hasText(item) && !"normal".equals(item)) {
          labels.add(item.trim());
        }
      }
    }
    if (labels.isEmpty()) {
      return "normal";
    }
    return String.join(",", labels);
  }

  private static String join(List<String> labels) {
    if (labels == null || labels.isEmpty()) {
      return "";
    }
    List<String> out = new ArrayList<>();
    for (String item : labels) {
      if (StringUtils.hasText(item) && !out.contains(item.trim())) {
        out.add(item.trim());
      }
    }
    return String.join(",", out);
  }

  private static String clip(String reason, String fallback) {
    return clip(reason, fallback, REASON_MAX);
  }

  private static String clip(String reason, String fallback, int max) {
    String note = StringUtils.hasText(reason) ? reason.trim() : fallback;
    if (note.length() > max) {
      return note.substring(0, max);
    }
    return note;
  }

  private static String blank(String value) {
    return value == null ? "" : value;
  }

  private record Hit(String verdict, String reason, Integer score, String labels) {}
}
