package com.campfires.community;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】参加申請の1件（join_requests テーブルの行に、申請した人のニックネームを足したもの）。
 * 【なぜ必要か】SQL の結果を受け取り、返事の形（JoinRequestResponse）に変えるため。
 *
 * @param id          参加申請の番号
 * @param userId      申請した人の番号
 * @param nickname    申請した人のニックネーム（アカウントを削除していれば null）
 * @param status      pending（申請中）、approved（承認済み）、rejected（却下）
 * @param requestedAt 申請した日時
 */
public record JoinRequestRow(long id, long userId, String nickname, String status, Instant requestedAt) {

    /**
     * 【何のため】返事の形に変える。
     */
    public JoinRequestResponse toResponse() {
        return new JoinRequestResponse(id, new UserRef(userId, nickname), status, requestedAt);
    }
}
