package com.campfires.account;

import com.campfires.common.validation.ValidPassword;

import jakarta.validation.constraints.NotEmpty;

/**
 * 【役割】パスワードの変更（PUT /api/me/password）で送られてくるデータ（docs/api/openapi.yaml の ChangePasswordRequest）。
 * 【なぜ必要か】本人の操作であることを確かめるための今のパスワードと、新しいパスワードを受け取り、
 * 入力のルールに合っているかを確かめるため。
 *
 * @param currentPassword 今のパスワード（本人の確認に使う。前後の空白も含めて、そのまま使う）
 * @param newPassword     新しいパスワード（アカウントの登録と同じルール：8〜64文字、半角の英字・数字・記号だけ）
 */
public record ChangePasswordRequest(
        @NotEmpty(message = "今のパスワードを入力してください。")
        String currentPassword,

        @ValidPassword
        String newPassword) {
}
