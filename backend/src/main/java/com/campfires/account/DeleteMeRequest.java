package com.campfires.account;

import jakarta.validation.constraints.NotEmpty;

/**
 * 【役割】アカウントの削除（DELETE /api/me）で送られてくるデータ（docs/api/openapi.yaml の DeleteMeRequest）。
 * 【なぜ必要か】本人の操作であることを確かめるため、パスワードをもう一度送ってもらう。
 *
 * @param password 今のパスワード。前後の空白も含めて、そのまま使う
 */
public record DeleteMeRequest(
        @NotEmpty(message = "パスワードを入力してください。")
        String password) {
}
