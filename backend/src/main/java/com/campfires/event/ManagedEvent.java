package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】管理しているイベントの一覧の1件（docs/api/openapi.yaml の ManagedEvent）。
 * 【なぜ必要か】左のメニューの「イベント編集」「申込者一覧」で、どのイベントを開くかを選ぶ画面（S-19）に使う。
 * 一覧の情報（EventSummary と同じ項目）に、承認待ちの人数と、自分の立場を足している。
 * SQL の結果を、そのまま入れる。
 *
 * @param id             イベントの番号
 * @param communityId    コミュニティの番号
 * @param communityName  コミュニティの名前
 * @param title          タイトル
 * @param startAt        開始日時
 * @param endAt          終了日時
 * @param capacity       定員
 * @param remainingSeats 残りの枠数
 * @param pendingCount   承認待ちの人数
 * @param myRole         そのコミュニティでの自分の立場（owner か staff）
 */
public record ManagedEvent(
        long id,
        long communityId,
        String communityName,
        String title,
        Instant startAt,
        Instant endAt,
        int capacity,
        long remainingSeats,
        long pendingCount,
        String myRole) {
}
