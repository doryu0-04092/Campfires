package com.campfires.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 【役割】コミュニティの作成・編集で送られてくるデータ（docs/api/openapi.yaml の CommunityInput）。
 * 【なぜ必要か】送られてきた JSON を Java のデータに変え、入力のルールに合っているかを確かめるため。
 * ルールと文は、偽の API（frontend/src/mocks/handlers/communities.ts）と同じにしている。
 *
 * @param name        名前（1〜50文字）
 * @param description 説明（2000文字まで。空でもよい）
 */
public record CommunityInput(
        @NotBlank(message = "コミュニティの名前を入力してください。")
        @Size(max = 50, message = "コミュニティの名前は50文字以内にしてください。")
        String name,

        @Size(max = 2000, message = "説明は2000文字以内にしてください。")
        String description) {

    /**
     * 【何のため】前後の空白を取り除く。説明が送られてこなかったときは、空の文字にする。
     */
    public CommunityInput {
        name = name == null ? null : name.strip();
        description = description == null ? "" : description.strip();
    }
}
