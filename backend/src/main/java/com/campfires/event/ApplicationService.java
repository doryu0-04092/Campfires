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
 * 【役割】イベントへの申込み、キャンセル、承認・却下、申込者の一覧の、業務の処理をするクラス。
 * 【なぜ必要か】申込みのルール（要件定義書 5.2、5.3、5.12）と、定員の同時実行制御（F-14）を、1か所にまとめるため。
 */
@Service
public class ApplicationService {

    private final EventService eventService;
    private final ApplicationMapper applicationMapper;
    private final ApplicationCancelService applicationCancelService;
    private final ApplicationApprovalService approvalService;
    private final CommunityGuard guard;

    public ApplicationService(EventService eventService, ApplicationMapper applicationMapper,
            ApplicationCancelService applicationCancelService, ApplicationApprovalService approvalService,
            CommunityGuard guard) {
        this.eventService = eventService;
        this.applicationMapper = applicationMapper;
        this.applicationCancelService = applicationCancelService;
        this.approvalService = approvalService;
        this.guard = guard;
    }

    /**
     * 【何のため】イベントに申し込む。だれが申し込めるか、すぐに席を取るかは、イベントの承認の方法で決まる（要件定義書 5.12）。
     * - コミュニティのメンバー（オーナー、運営スタッフを含む）は、memberApproval を使う
     * - メンバー以外は、nonMemberApproval を使う。closed（受け付けない）なら申し込めない
     * - auto（自動）：空きがあれば申込み済み、満席ならキャンセル待ち（F-15）
     * - manual（手動）：承認待ち。席は、承認されたときに取る
     *
     * 定員を超えないしくみ（F-14）：
     * 最初にイベントの行をロックする（SELECT … FOR UPDATE）。同じイベントに同時に申し込んだ人は、
     * 前の人の処理（人数を数えて、申込みを登録する）が終わるまで待つ。
     * そのため「数えたときは空いていたのに、登録したら定員を超えていた」ということが起きない。
     */
    @Transactional
    public MyApplication apply(long userId, long communityId, long eventId) {
        EventRow event = eventService.requireEventForUpdate(communityId, eventId);
        boolean member = guard.roleOf(communityId, userId) != null;
        String approval = event.approvalFor(member);
        if ("closed".equals(approval)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_MEMBER",
                    "このイベントに申し込むには、コミュニティのメンバーになる必要があります。");
        }
        MyApplication existing = applicationMapper.findMyApplication(eventId, userId);
        if (existing != null) {
            // 却下された人は、同じイベントに申し込み直せない
            throw "rejected".equals(existing.status())
                    ? ApiException.conflict("APPLICATION_REJECTED", "このイベントへの申込みは、却下されています。")
                    : alreadyApplied();
        }
        if (event.deadlinePassed()) {
            throw ApiException.conflict("APPLICATION_DEADLINE_PASSED", "申込みの締め切りを過ぎています。");
        }
        // 手動なら承認待ち。自動なら、空きがあれば申込み済み、なければキャンセル待ち
        String status;
        if ("manual".equals(approval)) {
            status = "pending";
        } else {
            status = event.appliedCount() < event.capacity() ? "applied" : "waitlisted";
        }
        try {
            applicationMapper.insert(eventId, userId, status);
        } catch (DuplicateKeyException e) {
            // 念のため：1人1回の制約（applications_one_per_user）で止まったとき
            throw alreadyApplied();
        }
        return applicationMapper.findMyApplication(eventId, userId);
    }

    /**
     * 【何のため】自分の申込み（承認待ち、キャンセル待ちを含む）を取り消す。却下された申込みは取り消せない。
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
     * 【何のため】承認待ちの申込みを、承認・却下する。オーナーと運営スタッフだけ。イベントの開始日時まで（要件定義書 5.12）。
     * 承認したときに席を取る（満席ならキャンセル待ち）。申し込んだ人にお知らせを送る。
     * @param status approved（承認）か rejected（却下）
     */
    @Transactional
    public ApplicationResponse decide(long managerId, long communityId, long eventId, long targetUserId,
            String status) {
        // イベントの行をロックして、承認と申込み・取り消しが同時に起きても、定員を超えないようにする
        EventRow event = eventService.requireEventForUpdate(communityId, eventId);
        guard.requireManager(guard.requireCommunity(communityId), managerId);
        ApplicationRow application = applicationMapper.findApplication(eventId, targetUserId);
        if (application == null) {
            throw ApiException.notFound("申込みが見つかりません。");
        }
        if (event.started()) {
            throw ApiException.conflict("EVENT_STARTED", "開始したイベントの申込みは、承認・却下できません。");
        }
        boolean decided = "approved".equals(status)
                ? approvalService.approve(communityId, eventId, targetUserId)
                : approvalService.reject(eventId, targetUserId);
        if (!decided) {
            throw ApiException.conflict("APPLICATION_ALREADY_DECIDED", "この申込みは、すでに承認または却下されています。");
        }
        return applicationMapper.findApplication(eventId, targetUserId).toResponse();
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
