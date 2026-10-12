package com.campfires.auth;

/**
 * 【役割】まだ使える、パスワードの再設定のトークン1つ分（password_reset_tokens テーブルから読んだ値）。
 * 【なぜ必要か】再設定のときに「どのトークンを使用済みにするか」「だれのパスワードを変えるか」を知るため。
 * トークンを変換した値（token_hash）は、探すときに使うだけなので、ここには持たない。
 *
 * @param id     トークンの番号
 * @param userId パスワードを再設定するユーザーの番号
 */
public record PasswordResetToken(long id, long userId) {
}
