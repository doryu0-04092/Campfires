package com.campfires.community;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】コミュニティの詳細の返事（docs/api/openapi.yaml の CommunityDetail）。
 * 【なぜ必要か】コミュニティの詳細の画面（S-05）で、情報と「自分との関係」（立場、参加申請の状態）を返すため。
 * 画面は myRole と myJoinRequestStatus を見て、ボタン（参加を申請する、退会する、管理画面へ）を出し分ける。
 *
 * @param id                  コミュニティの番号
 * @param name                名前
 * @param description         説明
 * @param memberCount         メンバーの人数
 * @param createdAt           作成した日時
 * @param owner               オーナー（ニックネームだけ）
 * @param archivedAt          アーカイブした日時。アーカイブしていなければ null
 * @param myRole              自分の立場（owner、staff、member）。メンバーでない、ログインしていなければ null
 * @param myJoinRequestStatus 自分の参加申請の状態（pending、rejected）。申請していない、メンバーなら null
 */
public record CommunityDetail(
        long id,
        String name,
        String description,
        long memberCount,
        Instant createdAt,
        UserRef owner,
        Instant archivedAt,
        String myRole,
        String myJoinRequestStatus) {

    /**
     * 【何のため】Community と、自分との関係から、返事を作る。
     */
    public static CommunityDetail of(Community community, String myRole, String myJoinRequestStatus) {
        return new CommunityDetail(community.id(), community.name(), community.description(),
                community.memberCount(), community.createdAt(),
                new UserRef(community.ownerId(), community.ownerNickname()),
                community.archivedAt(), myRole, myJoinRequestStatus);
    }
}
