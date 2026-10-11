package com.campfires.account;

import java.time.Instant;

/**
 * 【役割】users テーブルの1行を表すデータ。
 * 【なぜ必要か】データベースから読んだユーザーの情報を、Java の中で扱うため。
 * UserMapper.xml の resultMap で、テーブルの列とこの項目を結び付けている。
 *
 * record は「値をまとめて持つだけのクラス」を短く書く書き方。
 * （　）の中に書いた項目が、そのまま中身になり、user.nickname() のように読める。
 * アカウントを削除したユーザーは、realName、nickname、email、passwordHash が null（空）になる。
 *
 * @param id           ユーザーの番号
 * @param realName     本名
 * @param nickname     ニックネーム
 * @param email        メールアドレス
 * @param passwordHash パスワードを BCrypt で変換した値（元のパスワードには戻せない）
 * @param deletedAt    アカウントを削除した日時。削除していなければ null
 * @param createdAt    登録した日時
 */
public record User(
        long id,
        String realName,
        String nickname,
        String email,
        String passwordHash,
        Instant deletedAt,
        Instant createdAt) {
}
