package com.campfires.community;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】メンバーの返事（docs/api/openapi.yaml の Member）。
 * 【なぜ必要か】メンバーは、ニックネームだけで表す（本名とメールアドレスは返さない。docs/api/README.md 1.9）。
 *
 * @param user     メンバー
 * @param role     owner（オーナー）、staff（運営スタッフ）、member（メンバー）
 * @param joinedAt 参加した日時
 */
public record MemberResponse(UserRef user, String role, Instant joinedAt) {
}
