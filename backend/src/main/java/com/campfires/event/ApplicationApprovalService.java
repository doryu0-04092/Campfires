package com.campfires.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.notification.NotificationMapper;

/**
 * 【役割】承認待ちの申込みを、承認・却下する業務のルールのクラス。
 * 【なぜ必要か】承認は、オーナー・運営スタッフが1件ずつ行うとき（申込者一覧）と、
 * 承認の方法を「手動」から「自動」に変えたとき（イベントの編集）の、2か所で起きる。
 * 同じルールを1か所にまとめ、食い違いを防ぐ（要件定義書 5.12）。
 *
 * どのメソッドも、呼び出す側で先にイベントの行をロックしておくこと（EventService.requireEventForUpdate）。
 * ロックしておかないと、同時に承認したときに、定員を超えて「申込み済み」にしてしまうおそれがある。
 */
@Service
public class ApplicationApprovalService {

    private final EventMapper eventMapper;
    private final ApplicationMapper applicationMapper;
    private final NotificationMapper notificationMapper;

    public ApplicationApprovalService(EventMapper eventMapper, ApplicationMapper applicationMapper,
            NotificationMapper notificationMapper) {
        this.eventMapper = eventMapper;
        this.applicationMapper = applicationMapper;
        this.notificationMapper = notificationMapper;
    }

    /**
     * 【何のため】承認待ちの申込みを1件承認する。
     * 承認したときに席を確保する。空きがあれば申込み済み、満席ならキャンセル待ち（順番は承認された順）。
     * 申し込んだ人に、承認されたことを知らせる。
     * @return 承認したかどうか（承認待ちでなければ false）
     */
    @Transactional
    public boolean approve(long communityId, long eventId, long userId) {
        // 承認するたびに、申込み済みの人数を読み直す（まとめて承認するとき、前の人の分を数えるため）
        EventRow event = eventMapper.find(communityId, eventId);
        String status = event.appliedCount() < event.capacity() ? "applied" : "waitlisted";
        if (applicationMapper.approve(eventId, userId, status) == 0) {
            return false;
        }
        notificationMapper.insertApplicationDecided(eventId, userId, "application_approved");
        return true;
    }

    /**
     * 【何のため】承認待ちの申込みを1件却下する。申し込んだ人に、却下されたことを知らせる。
     * @return 却下したかどうか（承認待ちでなければ false）
     */
    @Transactional
    public boolean reject(long eventId, long userId) {
        if (applicationMapper.reject(eventId, userId) == 0) {
            return false;
        }
        notificationMapper.insertApplicationDecided(eventId, userId, "application_rejected");
        return true;
    }

    /**
     * 【何のため】承認待ちの申込みを、申し込んだ順に、まとめて承認する（承認の方法を「手動」から「自動」に変えたとき）。
     * @param member true ならコミュニティのメンバーの申込み、false ならメンバー以外の申込みを承認する
     */
    @Transactional
    public void approveAllPending(long communityId, long eventId, boolean member) {
        for (PendingApplication pending : applicationMapper.findPending(eventId, member)) {
            approve(communityId, eventId, pending.userId());
        }
    }
}
