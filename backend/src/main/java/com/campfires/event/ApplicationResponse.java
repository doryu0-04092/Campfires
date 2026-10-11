package com.campfires.event;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】申込者の一覧の1人分の返事（docs/api/openapi.yaml の Application）。
 * 【なぜ必要か】申込者は、ニックネームだけで表す（本名は返さない。要件定義書 5.6）。
 *
 * @param user             申し込んだ人
 * @param status           pending（承認待ち）、applied（申込み済み）、waitlisted（キャンセル待ち）のどれか
 * @param waitlistPosition キャンセル待ちの何番目か。申込み済みなら null
 * @param appliedAt        申し込んだ日時
 */
public record ApplicationResponse(UserRef user, String status, Integer waitlistPosition, Instant appliedAt) {
}
