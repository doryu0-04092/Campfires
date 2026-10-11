package com.campfires.notification;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】お知らせの1件（notifications テーブルの行に、出来事を起こした人のニックネームを足したもの）。
 * 【なぜ必要か】SQL の結果を受け取り、返事の形（NotificationResponse）に変えるため。
 *
 * @param id            お知らせの番号
 * @param type          種類（late_cancellation か event_deleted）
 * @param eventTitle    出来事が起きた時点のイベントのタイトル
 * @param communityId   コミュニティの番号
 * @param eventId       イベントの番号。イベントが削除されていれば null
 * @param actorUserId   出来事を起こした人の番号。いなければ null
 * @param actorNickname 出来事を起こした人のニックネーム（アカウントを削除していれば null）
 * @param readAt        既読にした日時。未読なら null
 * @param createdAt     お知らせができた日時
 */
public record NotificationRow(
        long id,
        String type,
        String eventTitle,
        long communityId,
        Long eventId,
        Long actorUserId,
        String actorNickname,
        Instant readAt,
        Instant createdAt) {

    /**
     * 【何のため】返事の形に変える。既読かどうかは、readAt に日時が入っているかで決める。
     */
    public NotificationResponse toResponse() {
        UserRef actor = actorUserId == null ? null : new UserRef(actorUserId, actorNickname);
        return new NotificationResponse(id, type, eventTitle, communityId, eventId, actor, readAt != null, createdAt);
    }
}
