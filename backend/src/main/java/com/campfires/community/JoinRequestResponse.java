package com.campfires.community;

import java.time.Instant;

import com.campfires.account.UserRef;

/**
 * 【役割】参加申請の返事（docs/api/openapi.yaml の JoinRequest）。
 * 【なぜ必要か】申請した人は、ニックネームだけで表す（本名とメールアドレスは返さない。docs/api/README.md 1.9）。
 *
 * @param id          参加申請の番号
 * @param user        申請した人
 * @param status      pending（申請中）、approved（承認済み）、rejected（却下）
 * @param requestedAt 申請した日時
 */
public record JoinRequestResponse(long id, UserRef user, String status, Instant requestedAt) {
}
