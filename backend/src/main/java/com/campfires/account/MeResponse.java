package com.campfires.account;

import java.time.Instant;

/**
 * 【役割】自分のアカウントの情報の返事（docs/api/openapi.yaml の Me）。
 * 【なぜ必要か】User には passwordHash（パスワードの変換した値）など、返事に入れてはいけない項目がある。
 * 返す項目だけを持つ形を別に作り、うっかり返してしまうことを防ぐ。
 * 本名とメールアドレスを返すのは、本人あての、この返事だけ（docs/api/README.md 1.9）。
 *
 * @param id        ユーザーの番号
 * @param realName  本名
 * @param nickname  ニックネーム
 * @param email     メールアドレス
 * @param createdAt 登録した日時（JSON では "2026-10-01T03:00:00Z" の形になる）
 */
public record MeResponse(long id, String realName, String nickname, String email, Instant createdAt) {

    /**
     * 【何のため】User から、返事の形に変える。
     * static のメソッドなので、MeResponse.from(user) のように呼べる。
     */
    public static MeResponse from(User user) {
        return new MeResponse(user.id(), user.realName(), user.nickname(), user.email(), user.createdAt());
    }
}
