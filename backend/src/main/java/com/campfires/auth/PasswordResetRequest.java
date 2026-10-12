package com.campfires.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 【役割】パスワードの再設定の依頼（POST /api/auth/password-reset-requests）で送られてくるデータ
 * （docs/api/openapi.yaml の PasswordResetRequest）。
 * 【なぜ必要か】送られてきた JSON を Java のデータに変え、メールアドレスの形を確かめるため。
 * 形のチェックは、アカウントの登録（SignupRequest）と同じ。形が正しくないときの 400 は、
 * 登録されているかどうかに関係なく返るので、メールアドレスを探る手がかりにはならない。
 *
 * @param email 再設定したいアカウントのメールアドレス（前後の空白は取り除く）
 */
public record PasswordResetRequest(
        @NotBlank(message = "メールアドレスを入力してください。")
        @Size(max = 254, message = "メールアドレスは254文字以内にしてください。")
        // 「空白と @ 以外の文字 → @ → 空白と @ 以外の文字 → . → 空白と @ 以外の文字」の形（空は @NotBlank に任せる）
        @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "メールアドレスの形が正しくありません。")
        String email) {

    /**
     * 【何のため】メールアドレスの前後の空白を取り除く（ログイン・登録と同じ扱い）。
     */
    public PasswordResetRequest {
        email = email == null ? null : email.strip();
    }
}
