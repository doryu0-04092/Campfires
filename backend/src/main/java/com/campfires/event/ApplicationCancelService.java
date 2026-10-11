package com.campfires.event;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.notification.NotificationMapper;

/**
 * 【役割】申込みを取り消して、その後の処理（繰り上げ、お知らせ）を行う、業務のルールのクラス。
 * 【なぜ必要か】申込みの取り消しは、キャンセル（F-07）、コミュニティの退会（5.4）、除名（5.4.1）、
 * アカウントの削除（5.5）の、いくつもの機能で起きる。同じルールを1か所にまとめ、直し忘れを防ぐ。
 * （偽の API の src/mocks/rules.ts の cancelApplication、cancelUpcomingApplications と同じルール）
 *
 * @Service は「業務の処理をするクラス」という印。Spring が1つだけ作って、必要な場所に渡してくれる。
 */
@Service
public class ApplicationCancelService {

    private final ApplicationMapper applicationMapper;
    private final NotificationMapper notificationMapper;

    /**
     * 【何のため】使う道具（Mapper）を受け取る。Spring が自動で渡してくれる（コンストラクター・インジェクション）。
     */
    public ApplicationCancelService(ApplicationMapper applicationMapper, NotificationMapper notificationMapper) {
        this.applicationMapper = applicationMapper;
        this.notificationMapper = notificationMapper;
    }

    /**
     * 【何のため】ユーザーの、開催前のイベントへの申込み（キャンセル待ちを含む）を、すべて取り消す。
     * @param userId         申込みを取り消すユーザー
     * @param operatorUserId 取り消しの操作をした人（退会・アカウントの削除なら本人、除名ならオーナー）
     * @param communityId    指定したときは、そのコミュニティのイベントだけ。null なら、すべて
     *
     * @Transactional は「この中の処理を1つのまとまり（トランザクション）にする」という印。
     * 途中でエラーが起きたら、それまでの変更をすべて取り消す。
     */
    @Transactional
    public void cancelUpcomingApplications(long userId, long operatorUserId, Long communityId) {
        // イベントの番号の小さい順に処理する（ロックを取る順番をそろえて、デッドロック＝お互いに待ち続ける状態を防ぐ）
        List<Long> eventIds = applicationMapper.findUpcomingEventIdsOfUser(userId, communityId);
        for (long eventId : eventIds) {
            cancel(eventId, userId, operatorUserId);
        }
    }

    /**
     * 【何のため】申込みを1件取り消し、要件定義書のルールどおりに後の処理をする。
     * - 締め切り前：申込み済みの人が取り消したときは、キャンセル待ちの先頭の人を繰り上げる（5.3）
     * - 締め切り後：繰り上げない。オーナーと運営スタッフにお知らせを送る（5.2、5.10）
     * @return 取り消したかどうか（申込みがなければ false）
     */
    @Transactional
    public boolean cancel(long eventId, long userId, long operatorUserId) {
        // 先にイベントの行をロックして、同じイベントの申込み・取り消しが同時に起きても、人数がずれないようにする
        applicationMapper.lockEvent(eventId);
        CancelTarget target = applicationMapper.findCancelTarget(eventId, userId);
        if (target == null) {
            return false;
        }
        applicationMapper.delete(target.applicationId());

        if (target.deadlinePassed()) {
            // 締め切り後のキャンセル：オーナーと運営スタッフに知らせる（操作した本人を除く）
            notificationMapper.insertLateCancellation(eventId, userId, operatorUserId);
        } else if (target.isApplied()) {
            // 締め切り前に、申込み済みの人が取り消した：空いた枠に、キャンセル待ちの人を繰り上げる
            applicationMapper.promoteWaitlist(eventId);
        }
        return true;
    }
}
