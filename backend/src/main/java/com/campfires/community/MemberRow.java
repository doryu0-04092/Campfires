package com.campfires.community;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】メンバーの1人（memberships テーブルの行に、ニックネームを足したもの）。
 * 【なぜ必要か】SQL の結果を受け取り、返事の形（MemberResponse）に変えるため。
 *
 * @param userId   ユーザーの番号
 * @param nickname ニックネーム（アカウントを削除していれば null）
 * @param role     owner（オーナー）、staff（運営スタッフ）、member（メンバー）
 * @param joinedAt 参加した日時
 */
public record MemberRow(long userId, String nickname, String role, Instant joinedAt) {

    /**
     * 【何のため】返事の形に変える。
     */
    public MemberResponse toResponse() {
        return new MemberResponse(new UserRef(userId, nickname), role, joinedAt);
    }
}
