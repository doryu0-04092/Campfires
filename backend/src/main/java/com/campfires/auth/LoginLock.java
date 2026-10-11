package com.campfires.auth;

/**
 * 【役割】ログインのロックの状態（login_failures テーブルから読んだ値）。
 * 【なぜ必要か】ログインの前に「ロック中か」「あと何秒で解けるか」を知るため。
 *
 * @param locked            今ロック中かどうか
 * @param retryAfterSeconds ロックが解けるまでの秒数（ロック中でなければ 0）
 */
public record LoginLock(boolean locked, long retryAfterSeconds) {
}
