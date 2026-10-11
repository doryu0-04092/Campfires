package com.campfires.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 【役割】ニックネームの変更（PATCH /api/me）で送られてくるデータ（docs/api/openapi.yaml の UpdateMeRequest）。
 * 【なぜ必要か】送られてきた JSON を Java のデータに変え、入力のルールに合っているかを確かめるため。
 *
 * @NotBlank、@Size は入力のチェックのルール（Bean Validation）。Controller で @Valid を付けると確かめられ、
 * 合わなければ GlobalExceptionHandler が 400（VALIDATION_ERROR）を返す。message が画面に表示される文。
 *
 * @param nickname 新しいニックネーム（1〜30文字）
 */
public record UpdateMeRequest(
        @NotBlank(message = "ニックネームを入力してください。")
        @Size(max = 30, message = "ニックネームは30文字以内にしてください。")
        String nickname) {

    /**
     * 【何のため】前後の空白を取り除く（偽の API と同じ扱い）。
     * record の「コンパクトコンストラクター」という書き方で、値を受け取ったときに一度だけ実行される。
     * strip() は、全角の空白も含めて、前後の空白を取り除く。
     */
    public UpdateMeRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
