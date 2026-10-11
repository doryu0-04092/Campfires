package com.campfires.notification;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】お知らせ1件の返事（docs/api/openapi.yaml の Notification）。
 * 【なぜ必要か】文章そのものではなく、種類と材料（イベントのタイトルなど）を返し、画面で文を組み立てる。
 * 画面の文言を変えるときに、サーバーを直さずに済むようにするため。
 *
 * @param id          お知らせの番号
 * @param type        種類（late_cancellation か event_deleted）
 * @param eventTitle  出来事が起きた時点のイベントのタイトル
 * @param communityId コミュニティの番号
 * @param eventId     イベントの番号。イベントが削除されていれば null
 * @param actor       出来事を起こした人（ニックネームだけ）。いなければ null
 * @param read        既読かどうか
 * @param createdAt   お知らせができた日時
 */
public record NotificationResponse(
        long id,
        String type,
        String eventTitle,
        long communityId,
        Long eventId,
        UserRef actor,
        boolean read,
        Instant createdAt) {
}
