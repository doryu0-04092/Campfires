package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】events テーブルの1行に、コミュニティの名前や申込みの人数などを足したデータ。
 * 【なぜ必要か】イベントの詳細の返事（EventDetail）を作るときや、
 * 「終了しているか」「締め切りを過ぎているか」「申込み済みは何人か」を確かめるときに使う。
 * 締め切りと終了の判断は、データベースの今の日時（now()）で行い、その結果を入れている。
 *
 * @param id                  イベントの番号
 * @param communityId         コミュニティの番号
 * @param communityName       コミュニティの名前
 * @param communityArchivedAt コミュニティをアーカイブした日時。アーカイブしていなければ null
 * @param title               タイトル
 * @param description         説明
 * @param startAt             開始日時
 * @param endAt               終了日時
 * @param applicationDeadline 申込み締め切り日時
 * @param location            場所
 * @param capacity            定員
 * @param appliedCount        申込み済みの人数（キャンセル待ちを除く）
 * @param waitlistCount       キャンセル待ちの人数
 * @param ended               終了しているかどうか
 * @param deadlinePassed      申込みの締め切りを過ぎているかどうか
 * @param createdAt           作成した日時
 */
public record EventRow(
        long id,
        long communityId,
        String communityName,
        Instant communityArchivedAt,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        Instant applicationDeadline,
        String location,
        int capacity,
        long appliedCount,
        long waitlistCount,
        boolean ended,
        boolean deadlinePassed,
        Instant createdAt) {
}
