package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】自分が申し込んだイベントの1件（イベントの情報と、自分の申込みの状況を、1行にまとめたもの）。
 * 【なぜ必要か】SQL の結果を受け取り、返事の形（MyEvent）に変えるため。
 *
 * @param id               イベントの番号
 * @param communityId      コミュニティの番号
 * @param communityName    コミュニティの名前
 * @param title            タイトル
 * @param startAt          開始日時
 * @param endAt            終了日時
 * @param capacity         定員
 * @param remainingSeats   残りの枠数
 * @param status           自分の申込みの状態（applied か waitlisted）
 * @param waitlistPosition キャンセル待ちの何番目か。申込み済みなら null
 * @param appliedAt        申し込んだ日時
 */
public record MyEventRow(
        long id,
        long communityId,
        String communityName,
        String title,
        Instant startAt,
        Instant endAt,
        int capacity,
        long remainingSeats,
        String status,
        Integer waitlistPosition,
        Instant appliedAt) {

    /**
     * 【何のため】返事の形に変える。
     */
    public MyEvent toResponse() {
        return new MyEvent(id, communityId, communityName, title, startAt, endAt, capacity, remainingSeats,
                new MyApplication(status, waitlistPosition, appliedAt));
    }
}
