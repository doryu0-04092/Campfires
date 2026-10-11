package com.campfires.community;

import java.time.Instant;

/**
 * 【役割】communities テーブルの1行に、メンバーの人数とオーナーのニックネームを足したデータ。
 * 【なぜ必要か】コミュニティの詳細の返事（CommunityDetail）を作るときや、
 * 「オーナーかどうか」「アーカイブ中かどうか」を確かめるときに使う。
 *
 * @param id            コミュニティの番号
 * @param name          名前
 * @param description   説明
 * @param ownerId       オーナーのユーザーの番号
 * @param ownerNickname オーナーのニックネーム（アカウントを削除していれば null）
 * @param memberCount   メンバーの人数
 * @param archivedAt    アーカイブした日時。アーカイブしていなければ null
 * @param createdAt     作成した日時
 */
public record Community(
        long id,
        String name,
        String description,
        long ownerId,
        String ownerNickname,
        long memberCount,
        Instant archivedAt,
        Instant createdAt) {

    /**
     * 【何のため】アーカイブ中かどうか。
     */
    public boolean isArchived() {
        return archivedAt != null;
    }

    /**
     * 【何のため】そのユーザーがオーナーかどうか。
     */
    public boolean isOwner(long userId) {
        return ownerId == userId;
    }
}
