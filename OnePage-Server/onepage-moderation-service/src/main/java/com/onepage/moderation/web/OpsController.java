package com.onepage.moderation.web;

import com.onepage.common.dto.UserSummary;
import com.onepage.common.web.CurrentUser;
import com.onepage.moderation.client.UserClient;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 运营后台。必须登录且通过 {@link StaffGuard}。 */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {
  private final ModerationService moderationService;
  private final StaffGuard staffGuard;
  private final UserClient userClient;
  private final String internalToken;

  public OpsController(
      ModerationService moderationService,
      StaffGuard staffGuard,
      UserClient userClient,
      @Value("${onepage.internal-token}") String internalToken) {
    this.moderationService = moderationService;
    this.staffGuard = staffGuard;
    this.userClient = userClient;
    this.internalToken = internalToken;
  }

  @GetMapping("/me")
  public UserSummary me(HttpServletRequest request) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    List<UserSummary> briefs = userClient.briefs(internalToken, List.of(userId));
    if (briefs == null || briefs.isEmpty()) {
      return new UserSummary(String.valueOf(userId), "", "", "");
    }
    return briefs.get(0);
  }

  @GetMapping("/reports")
  public ModerationDtos.CursorPage list(
      HttpServletRequest request,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.page(status, cursor, limit);
  }

  @PatchMapping("/reports/{reportId}")
  public ModerationDtos.ResolveResult resolve(
      HttpServletRequest request,
      @PathVariable("reportId") long reportId,
      @RequestBody ModerationDtos.ResolveRequest body) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.resolve(userId, reportId, body);
  }

  @GetMapping("/posts")
  public ModerationDtos.OpsPostPage posts(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "taken", required = false) String taken,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.opsPosts(q, taken, cursor, limit);
  }

  @GetMapping("/posts/{postId}")
  public ModerationDtos.OpsPostView post(
      HttpServletRequest request,
      @PathVariable("postId") long postId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.opsPost(postId);
  }

  @PostMapping("/posts/{postId}/takedown")
  public ModerationDtos.OpsPostView takedownPost(
      HttpServletRequest request,
      @PathVariable("postId") long postId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.takedownPost(postId);
  }

  @PostMapping("/posts/{postId}/restore")
  public ModerationDtos.OpsPostView restorePost(
      HttpServletRequest request,
      @PathVariable("postId") long postId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.restorePost(postId);
  }

  @GetMapping("/comments")
  public ModerationDtos.OpsCommentPage comments(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.opsComments(q, hidden, cursor, limit);
  }

  @PostMapping("/comments/{commentId}/hide")
  public ResponseEntity<Void> hideComment(
      HttpServletRequest request, @PathVariable("commentId") long commentId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    moderationService.hideComment(commentId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/comments/{commentId}/restore")
  public ResponseEntity<Void> restoreComment(
      HttpServletRequest request, @PathVariable("commentId") long commentId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    moderationService.restoreComment(commentId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/overview")
  public ModerationDtos.OpsOverview overview(HttpServletRequest request) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.overview();
  }

  @GetMapping("/catalog")
  public ModerationDtos.OpsCatalog catalog(HttpServletRequest request) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.catalog();
  }

  @PostMapping("/categories")
  public ModerationDtos.OpsCategory createCategory(
      HttpServletRequest request, @RequestBody ModerationDtos.CategorySpec spec) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.saveCategory(null, spec);
  }

  @PatchMapping("/categories/{id}")
  public ModerationDtos.OpsCategory patchCategory(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestBody ModerationDtos.CategorySpec spec) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.saveCategory(id, spec);
  }

  @PostMapping("/categories/{id}/enabled")
  public ResponseEntity<Void> categoryEnabled(
      HttpServletRequest request,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    moderationService.setCategoryEnabled(id, value);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/campaigns")
  public ModerationDtos.OpsCampaign createCampaign(
      HttpServletRequest request, @RequestBody ModerationDtos.CampaignSpec spec) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.saveCampaign(spec, true);
  }

  @PatchMapping("/campaigns/{eventId}")
  public ModerationDtos.OpsCampaign patchCampaign(
      HttpServletRequest request,
      @PathVariable("eventId") String eventId,
      @RequestBody ModerationDtos.CampaignSpec spec) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    ModerationDtos.CampaignSpec body = new ModerationDtos.CampaignSpec(
        eventId,
        spec.title(),
        spec.intro(),
        spec.coverUrl(),
        spec.status(),
        spec.sortOrder(),
        spec.enabled());
    return moderationService.saveCampaign(body, false);
  }

  @PostMapping("/campaigns/{eventId}/enabled")
  public ResponseEntity<Void> campaignEnabled(
      HttpServletRequest request,
      @PathVariable("eventId") String eventId,
      @RequestParam("value") boolean value) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    moderationService.setCampaignEnabled(eventId, value);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/topics")
  public ModerationDtos.OpsTopic createTopic(
      HttpServletRequest request, @RequestBody ModerationDtos.TopicSpec spec) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.saveTopic(null, spec);
  }

  @PatchMapping("/topics/{id}")
  public ModerationDtos.OpsTopic patchTopic(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestBody ModerationDtos.TopicSpec spec) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.saveTopic(id, spec);
  }

  @PostMapping("/topics/{id}/enabled")
  public ResponseEntity<Void> topicEnabled(
      HttpServletRequest request,
      @PathVariable("id") long id,
      @RequestParam("value") boolean value) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    moderationService.setTopicEnabled(id, value);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/users/{userId}")
  public ModerationDtos.OpsUser user(
      HttpServletRequest request,
      @PathVariable("userId") long targetId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.getUser(targetId);
  }

  @GetMapping("/users")
  public ModerationDtos.OpsUserPage users(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.pageUsers(q, cursor, limit);
  }

  @PostMapping("/users/{userId}/freeze")
  public ModerationDtos.OpsUser freeze(
      HttpServletRequest request,
      @PathVariable("userId") long targetId) {
    long actorId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(actorId);
    return moderationService.freezeUser(actorId, targetId, true);
  }

  @PostMapping("/users/{userId}/unfreeze")
  public ModerationDtos.OpsUser unfreeze(
      HttpServletRequest request,
      @PathVariable("userId") long targetId) {
    long actorId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(actorId);
    return moderationService.freezeUser(actorId, targetId, false);
  }

  @GetMapping("/conversations")
  public ModerationDtos.OpsConversationPage conversations(
      HttpServletRequest request,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.opsConversations(q, cursor, limit);
  }

  @GetMapping("/conversations/{conversationId}/messages")
  public ModerationDtos.OpsImMessagePage conversationMessages(
      HttpServletRequest request,
      @PathVariable("conversationId") long conversationId,
      @RequestParam(value = "hidden", required = false) String hidden,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    return moderationService.opsImMessages(conversationId, hidden, cursor, limit);
  }

  @PostMapping("/messages/{messageId}/hide")
  public ResponseEntity<Void> hideMessage(
      HttpServletRequest request, @PathVariable("messageId") long messageId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    moderationService.hideImMessage(messageId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/messages/{messageId}/restore")
  public ResponseEntity<Void> restoreMessage(
      HttpServletRequest request, @PathVariable("messageId") long messageId) {
    long userId = CurrentUser.requireUserId(request);
    staffGuard.requireStaff(userId);
    moderationService.restoreImMessage(messageId);
    return ResponseEntity.noContent().build();
  }
}
