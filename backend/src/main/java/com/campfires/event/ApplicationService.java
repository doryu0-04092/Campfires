package com.campfires.event;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.common.web.ApiException;
import com.campfires.common.web.PageResponse;
import com.campfires.common.web.Paging;
import com.campfires.community.CommunityGuard;

/**
 * 【役割】イベントへの申込み、キャンセル、申込者の一覧の、業務の処理をするクラス。
 * 【なぜ必要か】申込みのルール（要件定義書 5.2、5.3）と、定員の同時実行制御（F-14）を、1か所にまとめるため。
 */
@Service
public class ApplicationService {

    private final EventService eventService;
    private final ApplicationMapper applicationMapper;
    private final ApplicationCancelService applicationCancelService;
    private final CommunityGuard guard;

    public ApplicationService(EventService eventService, ApplicationMapper applicationMapper,
            ApplicationCancelService applicationCancelService, CommunityGuard guard) {
        this.eventService = eventService;
        this.applicationMapper = applicationMapper;
        this.applicationCancelService = applicationCancelService;
        this.guard = guard;
    }

    /**
     * 【何のため】イベントに申し込む。コミュニティのメンバー（オーナー、運営スタッフを含む）だけ。
     * 空きがあれば申込み済み、満席ならキャンセル待ちになる（F-15）。
     *
     * 定員を超えないしくみ（F-14）：
     * 最初にイベントの行をロックする（SELECT … FOR UPDATE）。同じイベントに同時に申し込んだ人は、
     * 前の人の処理（人数を数えて、申込みを登録する）が終わるまで待つ。
     * そのため「数えたときは空いていたのに、登録したら定員を超えていた」ということが起きない。
     */
    @Transactional
    public MyApplication apply(long userId, long communityId, long eventId) {
        EventRow event = eventService.requireEventForUpdate(communityId, eventId);
        if (guard.roleOf(communityId, userId) == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_MEMBER",
                    "イベントに申し込むには、コミュニティのメンバーになる必要があります。");
        }
        if (applicationMapper.findMyApplication(eventId, userId) != null) {
            throw alreadyApplied();
        }
        if (event.deadlinePassed()) {
            throw ApiException.conflict("APPLICATION_DEADLINE_PASSED", "申込みの締め切りを過ぎています。");
        }
        // 空きがあれば申込み済み、なければキャンセル待ち
        String status = event.appliedCount() < event.capacity() ? "applied" : "waitlisted";
        try {
            applicationMapper.insert(eventId, userId, status);
        } catch (DuplicateKeyException e) {
            // 念のため：1人1回の制約（applications_one_per_user）で止まったとき
            throw alreadyApplied();
        }
        return applicationMapper.findMyApplication(eventId, userId);
    }

    /**
     * 【何のため】自分の申込み（キャンセル待ちを含む）を取り消す。
     * 締め切り前なら繰り上げ、締め切り後ならオーナーと運営スタッフにお知らせ（ApplicationCancelService）。
     */
    @Transactional
    public void cancel(long userId, long communityId, long eventId) {
        EventRow event = eventService.requireEventForUpdate(communityId, eventId);
        if (event.ended()) {
            throw ApiException.conflict("EVENT_ENDED", "終了したイベントの申込みは、取り消せません。");
        }
        if (!applicationCancelService.cancel(eventId, userId, userId)) {
            throw ApiException.conflict("NOT_APPLIED", "このイベントには申し込んでいません。");
        }
    }

    /**
     * 【何のため】申込者の一覧を返す。オーナーと運営スタッフだけ。終了したイベントでも見られる。
     */
    public PageResponse<ApplicationResponse> listApplications(long userId, long communityId, long eventId,
            String pageValue) {
        eventService.requireEvent(communityId, eventId);
        guard.requireManager(guard.requireCommunity(communityId), userId);
        int page = Paging.parsePage(pageValue);
        long total = applicationMapper.countApplications(eventId);
        var items = applicationMapper.findApplications(eventId, Paging.PAGE_SIZE, Paging.offset(page)).stream()
                .map(ApplicationRow::toResponse)
                .toList();
        return PageResponse.of(items, total, page);
    }

    private static ApiException alreadyApplied() {
        return ApiException.conflict("ALREADY_APPLIED", "すでに申し込んでいます。");
    }
}
