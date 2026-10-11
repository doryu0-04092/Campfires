package com.campfires.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 【役割】アカウントの登録（POST /api/auth/signup）で送られてくるデータ（docs/api/openapi.yaml の SignupRequest）。
 * 【なぜ必要か】送られてきた JSON を Java のデータに変え、入力のルールに合っているかを確かめるため。
 * ルールと文は、偽の API（frontend/src/mocks/handlers/validation.ts）と同じにしている。
 *
 * @NotBlank（空ではない）、@Size（文字数）、@Pattern（決まった形）、@AssertTrue（true であること）は、
 * 入力のチェックのルール（Bean Validation）。message が画面に表示される文。
 *
 * @param realName         本名（1〜50文字）
 * @param nickname         ニックネーム（1〜30文字）
 * @param email            メールアドレス（254文字まで）
 * @param password         パスワード（8〜64文字、半角の英字・数字・記号だけ）
 * @param agreedToPolicies プライバシーポリシーと利用規約に同意したかどうか（true が必要）
 */
public record SignupRequest(
        @NotBlank(message = "本名を入力してください。")
        @Size(max = 50, message = "本名は50文字以内にしてください。")
        String realName,

        @NotBlank(message = "ニックネームを入力してください。")
        @Size(max = 30, message = "ニックネームは30文字以内にしてください。")
        String nickname,

        @NotBlank(message = "メールアドレスを入力してください。")
        @Size(max = 254, message = "メールアドレスは254文字以内にしてください。")
        // 「空白と @ 以外の文字 → @ → 空白と @ 以外の文字 → . → 空白と @ 以外の文字」の形。
        // 空のときは @NotBlank の文だけを出したいので、空（^$ の部分）もここでは通す
        @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "メールアドレスの形が正しくありません。")
        String email,

        @NotNull(message = "パスワードは8〜64文字にしてください。")
        @Size(min = 8, max = 64, message = "パスワードは8〜64文字にしてください。")
        // \x21-\x7E は、半角の英字・数字・記号（空白を除く）の範囲
        @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "パスワードには、半角の英字・数字・記号だけを使ってください。")
        String password,

        @NotNull(message = "プライバシーポリシーと利用規約に同意してください。")
        @AssertTrue(message = "プライバシーポリシーと利用規約に同意してください。")
        Boolean agreedToPolicies) {

    /**
     * 【何のため】本名、ニックネーム、メールアドレスの前後の空白を取り除く（偽の API と同じ扱い）。
     * パスワードは、前後の空白も含めてそのまま使う。
     * record の「コンパクトコンストラクター」という書き方で、値を受け取ったときに一度だけ実行される。
     */
    public SignupRequest {
        realName = strip(realName);
        nickname = strip(nickname);
        email = strip(email);
    }

    // null のときは null のまま、文字なら前後の空白（全角の空白も含む）を取り除く
    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
