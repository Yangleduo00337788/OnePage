package com.onepage.moderation.client;

import com.onepage.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 下架/恢复帖、工单标题、运营预览：改/读 content 库。目录更新走 POST，不要 PATCH。 */
@FeignClient(name = "onepage-content-service")
public interface ContentClient {
  @PostMapping("/internal/posts/{postId}/takedown")
  void takedown(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @PostMapping("/internal/posts/{postId}/restore")
  void restore(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @PostMapping("/internal/posts/{postId}/approve")
  void approve(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @PostMapping("/internal/posts/{postId}/reject")
  void reject(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId,
      @RequestParam(value = "reason", required = false) String reason,
      @RequestParam(value = "handlerId", required = false) Long handlerId);

  @GetMapping("/internal/posts/{postId}/ops")
  OpsPostView opsPost(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("postId") long postId);

  @GetMapping("/internal/posts/briefs")
  List<PostBrief> postBriefs(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam("ids") List<Long> ids);

  @GetMapping("/internal/posts/ops")
  OpsPostPage opsPosts(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "taken", required = false) String taken,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @GetMapping("/internal/catalog/ops")
  OpsCatalog catalog(@RequestHeader("X-Internal-Token") String token);

  @GetMapping("/internal/stats")
  ContentStats stats(@RequestHeader("X-Internal-Token") String token);

  @PostMapping("/internal/categories")
  OpsCategory createCategory(@RequestHeader("X-Internal-Token") String token, @RequestBody CategorySpec spec);

  @PostMapping("/internal/categories/{id}")
  OpsCategory patchCategory(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") String id,
      @RequestBody CategorySpec spec);

  @PostMapping("/internal/categories/{id}/enabled")
  void categoryEnabled(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value);

  @PostMapping("/internal/campaigns")
  OpsCampaign createCampaign(@RequestHeader("X-Internal-Token") String token, @RequestBody CampaignSpec spec);

  @PostMapping("/internal/campaigns/{eventId}")
  OpsCampaign patchCampaign(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("eventId") String eventId,
      @RequestBody CampaignSpec spec);

  @PostMapping("/internal/campaigns/{eventId}/enabled")
  void campaignEnabled(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("eventId") String eventId,
      @RequestParam("value") boolean value);

  @PostMapping("/internal/topics")
  OpsTopic createTopic(@RequestHeader("X-Internal-Token") String token, @RequestBody TopicSpec spec);

  @PostMapping("/internal/topics/{id}")
  OpsTopic patchTopic(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") String id,
      @RequestBody TopicSpec spec);

  @PostMapping("/internal/topics/{id}/enabled")
  void topicEnabled(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value);

  @PostMapping("/internal/notices")
  OpsNotice createNotice(@RequestHeader("X-Internal-Token") String token, @RequestBody NoticeSpec spec);

  @PostMapping("/internal/notices/{id}")
  OpsNotice patchNotice(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") String id,
      @RequestBody NoticeSpec spec);

  @PostMapping("/internal/notices/{id}/enabled")
  void noticeEnabled(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value);

  @GetMapping("/internal/faqs")
  List<OpsFaq> faqs(@RequestHeader("X-Internal-Token") String token);

  @PostMapping("/internal/faqs")
  OpsFaq createFaq(@RequestHeader("X-Internal-Token") String token, @RequestBody FaqSpec spec);

  @PostMapping("/internal/faqs/{id}")
  OpsFaq patchFaq(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") String id,
      @RequestBody FaqSpec spec);

  @PostMapping("/internal/faqs/{id}/enabled")
  void faqEnabled(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value);

  @GetMapping("/internal/media/ops")
  OpsMediaPage opsMedia(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "purpose", required = false) String purpose,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @PostMapping("/internal/media/{mediaId}/hide")
  void hideMedia(@RequestHeader("X-Internal-Token") String token, @PathVariable("mediaId") long mediaId);

  @PostMapping("/internal/media/{mediaId}/restore")
  void restoreMedia(@RequestHeader("X-Internal-Token") String token, @PathVariable("mediaId") long mediaId);

  @GetMapping("/internal/comments/briefs")
  List<OpsCommentRow> commentBriefs(
      @RequestHeader("X-Internal-Token") String token, @RequestParam("ids") List<Long> ids);

  @GetMapping("/internal/comments/ops")
  OpsCommentPage opsComments(
      @RequestHeader("X-Internal-Token") String token,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit);

  @PostMapping("/internal/comments/{commentId}/hide")
  void hideComment(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("commentId") long commentId);

  @PostMapping("/internal/comments/{commentId}/restore")
  void restoreComment(
      @RequestHeader("X-Internal-Token") String token,
      @PathVariable("commentId") long commentId);

  record PostBrief(String postId, String title, String authorId, boolean takenDown) {}

  record OpsPostRow(
      String postId,
      UserSummary author,
      String title,
      String postType,
      String category,
      boolean takenDown,
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}

  record OpsPostPage(List<OpsPostRow> items, String nextCursor, boolean hasMore) {}

  record OpsCommentRow(
      String commentId,
      String postId,
      UserSummary author,
      String content,
      boolean hidden,
      String createdAt) {}

  record OpsCommentPage(List<OpsCommentRow> items, String nextCursor, boolean hasMore) {}

  record OpsCampaign(
      String eventId,
      String title,
      String intro,
      String coverUrl,
      String status,
      int sortOrder,
      boolean enabled) {}

  record OpsCategory(String categoryId, String name, int sortOrder, boolean enabled) {}

  record OpsTopic(String topicId, String name, boolean official, int sortOrder, boolean enabled, long postCount) {}

  record OpsNotice(String noticeId, String title, String body, int sortOrder, boolean enabled) {}

  record OpsCatalog(
      List<OpsCategory> categories,
      List<OpsCampaign> campaigns,
      List<OpsTopic> topics,
      List<OpsNotice> notices) {}

  record ContentStats(
      long postsLive,
      long postsDown,
      long postsPending,
      long postsRejected,
      long comments,
      long postsToday,
      long commentsToday) {}

  record CategorySpec(String name, Integer sortOrder, Boolean enabled) {}

  record CampaignSpec(
      String eventId, String title, String intro, String coverUrl, String status, Integer sortOrder, Boolean enabled) {}

  record TopicSpec(String name, Boolean official, Integer sortOrder, Boolean enabled) {}

  record NoticeSpec(String title, String body, Integer sortOrder, Boolean enabled) {}

  record OpsFaq(
      String faqId,
      String groupId,
      String groupTitle,
      String question,
      String answer,
      int sortOrder,
      boolean enabled) {}

  record FaqSpec(String groupId, String question, String answer, Integer sortOrder, Boolean enabled) {}

  record OpsMediaRow(
      String mediaId,
      UserSummary owner,
      String purpose,
      String mimeType,
      long sizeBytes,
      String status,
      String url,
      String postId,
      boolean hidden,
      String createdAt) {}

  record OpsMediaPage(List<OpsMediaRow> items, String nextCursor, boolean hasMore) {}

  record MediaItem(String mediaId, String url, String mimeType, String role) {}

  record OpsPostView(
      String postId,
      UserSummary author,
      String title,
      String body,
      String coverUrl,
      String postType,
      String category,
      boolean original,
      String contentKind,
      String eventTitle,
      String visibility,
      List<MediaItem> media,
      boolean takenDown,
      String publishedAt,
      String reviewStatus,
      String rejectReason) {}
}
