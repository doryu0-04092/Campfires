package com.campfires.community;

import java.time.Instant;

/**
 * 【役割】一覧に表示するコミュニティの情報（docs/api/openapi.yaml の CommunitySummary）。
 * 【なぜ必要か】コミュニティの一覧・検索（S-17）と、参加しているコミュニティの一覧で返す形。
 * SQL の結果を、そのまま入れる（列の名前と項目の名前で結び付く）。
 *
 * @param id          コミュニティの番号
 * @param name        名前
 * @param description 説明
 * @param memberCount メンバーの人数（オーナー、運営スタッフを含む）
 * @param createdAt   作成した日時
 */
public record CommunitySummary(long id, String name, String description, long memberCount, Instant createdAt) {
}
