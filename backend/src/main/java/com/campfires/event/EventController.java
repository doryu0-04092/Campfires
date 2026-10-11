package com.campfires.event;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campfires.common.security.LoginUser;
import com.campfires.common.web.PageResponse;
import com.campfires.common.web.SearchPageResponse;

/**
 * 【役割】イベントと申込みの API（docs/api/openapi.yaml の events、applications と、account の /api/me/events）。
 * 【なぜ必要か】画面から届いたデータを受け取り、EventService と ApplicationService に処理を頼み、結果を返事にするため。
 *
 * イベントの作成・編集のデータは、日時の前後関係も含めて EventInputValidator でまとめてチェックするので、
 * ここでは @Valid を付けない。
 */
@RestController
public class EventController {

    private final EventService eventService;
    private final ApplicationService applicationService;

    public EventController(EventService eventService, ApplicationService applicationService) {
        this.eventService = eventService;
        this.applicationService = applicationService;
    }

    // ---------- イベント ----------

    /**
     * 【何のため】開催前のイベントの一覧・検索（GET /api/events）。ログインしていなくても呼べる。
     */
    @GetMapping("/api/events")
    public SearchPageResponse<EventSummary> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) String page) {
        return eventService.search(q, page);
    }

    /**
     * 【何のため】自分が申し込んだ、開催前のイベントの一覧（GET /api/me/events）。
     */
    @GetMapping("/api/me/events")
    public MyEventListResponse myEvents(@AuthenticationPrincipal LoginUser loginUser) {
        return eventService.findMyEvents(loginUser.id());
    }

    /**
     * 【何のため】コミュニティのイベントの一覧（GET /api/communities/{communityId}/events）。ログインしていなくても呼べる。
     */
    @GetMapping("/api/communities/{communityId}/events")
    public PageResponse<EventSummary> communityEvents(@PathVariable long communityId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String page) {
        return eventService.findCommunityEvents(communityId, status, page);
    }

    /**
     * 【何のため】イベントを作る（POST /api/communities/{communityId}/events）。201 と Location を返す。
     */
    @PostMapping("/api/communities/{communityId}/events")
    public ResponseEntity<EventDetail> create(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId, @RequestBody EventInput body) {
        EventDetail detail = eventService.create(loginUser.id(), communityId, body);
        return ResponseEntity.created(URI.create("/api/communities/" + communityId + "/events/" + detail.id()))
                .body(detail);
    }

    /**
     * 【何のため】イベントの詳細（GET /api/communities/{communityId}/events/{eventId}）。ログインしていなくても呼べる。
     */
    @GetMapping("/api/communities/{communityId}/events/{eventId}")
    public EventDetail get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId,
            @PathVariable long eventId) {
        return eventService.get(communityId, eventId, loginUser == null ? null : loginUser.id());
    }

    /**
     * 【何のため】イベントを編集する（PATCH /api/communities/{communityId}/events/{eventId}）。
     */
    @PatchMapping("/api/communities/{communityId}/events/{eventId}")
    public EventDetail update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId,
            @PathVariable long eventId, @RequestBody EventInput body) {
        return eventService.update(loginUser.id(), communityId, eventId, body);
    }

    /**
     * 【何のため】イベントを削除する（DELETE /api/communities/{communityId}/events/{eventId}）。204 を返す。
     */
    @DeleteMapping("/api/communities/{communityId}/events/{eventId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId,
            @PathVariable long eventId) {
        eventService.delete(loginUser.id(), communityId, eventId);
        return ResponseEntity.noContent().build();
    }

    // ---------- 申込み ----------

    /**
     * 【何のため】申込者の一覧（GET /api/communities/{communityId}/events/{eventId}/applications）。
     */
    @GetMapping("/api/communities/{communityId}/events/{eventId}/applications")
    public PageResponse<ApplicationResponse> applications(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId, @PathVariable long eventId, @RequestParam(required = false) String page) {
        return applicationService.listApplications(loginUser.id(), communityId, eventId, page);
    }

    /**
     * 【何のため】イベントに申し込む（POST /api/communities/{communityId}/events/{eventId}/applications）。201 を返す。
     */
    @PostMapping("/api/communities/{communityId}/events/{eventId}/applications")
    public ResponseEntity<MyApplication> apply(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId, @PathVariable long eventId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.apply(loginUser.id(), communityId, eventId));
    }

    /**
     * 【何のため】自分の申込みを取り消す（DELETE /api/communities/{communityId}/events/{eventId}/my-application）。
     */
    @DeleteMapping("/api/communities/{communityId}/events/{eventId}/my-application")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId,
            @PathVariable long eventId) {
        applicationService.cancel(loginUser.id(), communityId, eventId);
        return ResponseEntity.noContent().build();
    }
}
