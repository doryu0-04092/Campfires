package com.campfires.community;

import java.time.Instant;

/**
 * 【役割】管理しているコミュニティの一覧（S-16）の1件（docs/api/openapi.yaml の ManagedCommunity）。
 * 【なぜ必要か】オーナーか運営スタッフをしているコミュニティを、立場と申請中の件数と一緒に表示するため。
 * SQL の結果を、そのまま入れる。
 *
 * @param id                      コミュニティの番号
 * @param name                    名前
 * @param memberCount             メンバーの人数
 * @param archivedAt              アーカイブした日時。アーカイブしていなければ null
 * @param myRole                  自分の立場（owner か staff）
 * @param pendingJoinRequestCount 申請中の参加申請の件数
 */
public record ManagedCommunity(
        long id,
        String name,
        long memberCount,
        Instant archivedAt,
        String myRole,
        long pendingJoinRequestCount) {
}
