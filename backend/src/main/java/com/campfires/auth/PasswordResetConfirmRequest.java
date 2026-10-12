package com.campfires.auth;

import com.campfires.common.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 【役割】パスワードの再設定（POST /api/auth/password-resets）で送られてくるデータ
 * （docs/api/openapi.yaml の PasswordResetConfirmRequest）。
 * 【なぜ必要か】リンクに入っていたトークンと、新しいパスワードを受け取り、入力のルールに合っているかを確かめるため。
 *
 * @param token       再設定のリンクに入っていたトークン（43文字の英数字と「-」「_」）。長すぎるものは、探す前に断る
 * @param newPassword 新しいパスワード（アカウントの登録と同じルール。前後の空白も含めて、そのまま使う）
 */
public record PasswordResetConfirmRequest(
        @NotBlank(message = "再設定のリンクが正しくありません。メールのリンクを、もう一度開いてください。")
        @Size(max = 100, message = "再設定のリンクが正しくありません。メールのリンクを、もう一度開いてください。")
        String token,

        @ValidPassword
        String newPassword) {
}
