package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】一覧に表示するイベントの情報（docs/api/openapi.yaml の EventSummary）。
 * 【なぜ必要か】イベントの一覧・検索（S-01）と、コミュニティのイベントの一覧（S-05）で返す形。
 * SQL の結果を、そのまま入れる（列の名前と項目の名前で結び付く）。
 *
 * @param id             イベントの番号
 * @param communityId    どのコミュニティのイベントか
 * @param communityName  コミュニティの名前
 * @param title          タイトル
 * @param startAt        開始日時
 * @param endAt          終了日時
 * @param capacity       定員
 * @param remainingSeats 残りの枠数（定員 - 申込み済みの人数。0 なら満席）
 */
public record EventSummary(
        long id,
        long communityId,
        String communityName,
        String title,
        Instant startAt,
        Instant endAt,
        int capacity,
        long remainingSeats) {
}
