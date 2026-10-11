package com.campfires.event;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】申込者の一覧の1人分（申込みの行に、ニックネームとキャンセル待ちの順番を足したもの）。
 * 【なぜ必要か】SQL の結果を受け取り、返事の形（ApplicationResponse）に変えるため。
 *
 * @param userId           申し込んだ人の番号
 * @param nickname         ニックネーム（アカウントを削除していれば null）
 * @param status           applied（申込み済み）か waitlisted（キャンセル待ち）
 * @param waitlistPosition キャンセル待ちの何番目か。申込み済みなら null
 * @param appliedAt        申し込んだ日時
 */
public record ApplicationRow(long userId, String nickname, String status, Integer waitlistPosition, Instant appliedAt) {

    /**
     * 【何のため】返事の形に変える。
     */
    public ApplicationResponse toResponse() {
        return new ApplicationResponse(new UserRef(userId, nickname), status, waitlistPosition,
                appliedAt);
    }
}
