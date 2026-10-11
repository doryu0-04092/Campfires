package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】チェックに通った、イベントの作成・編集のデータ（日時を Instant＝日時の型に変えたもの）。
 * 【なぜ必要か】チェックの前（EventInput）と後を、別の形にしておくと、
 * チェックしていないデータを、うっかりデータベースに保存することを防げる。
 *
 * @param title               タイトル
 * @param description         説明
 * @param startAt             開始日時
 * @param endAt               終了日時
 * @param applicationDeadline 申込み締め切り日時
 * @param location            場所
 * @param capacity            定員
 */
public record ValidEventInput(
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        Instant applicationDeadline,
        String location,
        int capacity) {
}
