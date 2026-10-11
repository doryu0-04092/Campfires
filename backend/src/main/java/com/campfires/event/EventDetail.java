package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】イベントの詳細の返事（docs/api/openapi.yaml の EventDetail）。
 * 【なぜ必要か】イベントの詳細の画面（S-09）で、情報と「自分との関係」（立場、申込みの状況）を返すため。
 * 画面は myRole と myApplication を見て、ボタン（申し込む、キャンセルする、管理画面へ）を出し分ける。
 *
 * @param id                  イベントの番号
 * @param communityId         コミュニティの番号
 * @param communityName       コミュニティの名前
 * @param title               タイトル
 * @param startAt             開始日時
 * @param endAt               終了日時
 * @param capacity            定員
 * @param remainingSeats      残りの枠数
 * @param description         説明
 * @param location            場所
 * @param applicationDeadline 申込み締め切り日時
 * @param waitlistCount       キャンセル待ちの人数
 * @param pendingCount        承認待ちの人数
 * @param memberApproval      コミュニティのメンバーの承認の方法（auto：自動、manual：手動）
 * @param nonMemberApproval   メンバー以外の承認の方法（auto：自動、manual：手動、closed：受け付けない）
 * @param communityArchivedAt コミュニティをアーカイブした日時。アーカイブしていなければ null
 * @param myRole              コミュニティでの自分の立場。メンバーでない、ログインしていなければ null
 * @param myApplication       自分の申込みの状況。申し込んでいない、ログインしていなければ null
 * @param createdAt           作成した日時
 */
public record EventDetail(
        long id,
        long communityId,
        String communityName,
        String title,
        Instant startAt,
        Instant endAt,
        int capacity,
        long remainingSeats,
        String description,
        String location,
        Instant applicationDeadline,
        long waitlistCount,
        long pendingCount,
        String memberApproval,
        String nonMemberApproval,
        Instant communityArchivedAt,
        String myRole,
        MyApplication myApplication,
        Instant createdAt) {

    /**
     * 【何のため】EventRow と、自分との関係から、返事を作る。
     */
    public static EventDetail of(EventRow row, String myRole, MyApplication myApplication) {
        return new EventDetail(row.id(), row.communityId(), row.communityName(), row.title(), row.startAt(),
                row.endAt(), row.capacity(), row.capacity() - row.appliedCount(), row.description(), row.location(),
                row.applicationDeadline(), row.waitlistCount(), row.pendingCount(), row.memberApproval(),
                row.nonMemberApproval(), row.communityArchivedAt(), myRole, myApplication,
                row.createdAt());
    }
}
