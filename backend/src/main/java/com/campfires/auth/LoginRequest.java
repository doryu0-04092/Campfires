package com.campfires.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 【役割】ログイン（POST /api/auth/login）で送られてくるデータ（docs/api/openapi.yaml の LoginRequest）。
 * 【なぜ必要か】送られてきた JSON を Java のデータに変え、空の項目がないかを確かめるため。
 * 文字数などの細かいチェックはしない（どちらにしても、登録されている組み合わせと一致しなければログインできないため）。
 *
 * @param email    メールアドレス（前後の空白は取り除く）
 * @param password パスワード（そのまま使う）
 */
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください。")
        String email,

        @NotEmpty(message = "パスワードを入力してください。")
        String password) {

    /**
     * 【何のため】メールアドレスの前後の空白を取り除く（偽の API と同じ扱い）。
     */
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
