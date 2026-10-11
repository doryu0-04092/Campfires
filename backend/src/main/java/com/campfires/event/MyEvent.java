package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】自分が申し込んだイベントの1件の返事（docs/api/openapi.yaml の MyEvent）。
 * 【なぜ必要か】一覧の情報（EventSummary と同じ項目）に、自分の申込みの状況を足して返すため。
 *
 * @param id             イベントの番号
 * @param communityId    コミュニティの番号
 * @param communityName  コミュニティの名前
 * @param title          タイトル
 * @param startAt        開始日時
 * @param endAt          終了日時
 * @param capacity       定員
 * @param remainingSeats 残りの枠数
 * @param myApplication  自分の申込みの状況
 */
public record MyEvent(
        long id,
        long communityId,
        String communityName,
        String title,
        Instant startAt,
        Instant endAt,
        int capacity,
        long remainingSeats,
        MyApplication myApplication) {
}
