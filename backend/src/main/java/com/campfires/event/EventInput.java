package com.campfires.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 【役割】イベントの作成・編集で送られてくるデータ（docs/api/openapi.yaml の EventInput）。
 * 【なぜ必要か】送られてきた JSON を Java のデータに変え、入力のルールに合っているかを確かめるため。
 * ルールと文は、偽の API（frontend/src/mocks/handlers/events.ts）と同じにしている。
 *
 * 日時は文字のまま受け取り、EventInputValidator で日時に変える。
 * 日時の形が正しくないときも、ほかの項目のエラーと一緒に「項目ごとのエラー」として返すため。
 *
 * @param title               タイトル（1〜100文字）
 * @param description         説明（5000文字まで。空でもよい）
 * @param startAt             開始日時（例：2026-11-01T10:00:00Z）
 * @param endAt               終了日時
 * @param applicationDeadline 申込み締め切り日時（キャンセルの期限も兼ねる）
 * @param location            場所（1〜200文字）
 * @param capacity            定員（1〜1000人）
 */
public record EventInput(
        @NotBlank(message = "タイトルを入力してください。")
        @Size(max = 100, message = "タイトルは100文字以内にしてください。")
        String title,

        @Size(max = 5000, message = "説明は5000文字以内にしてください。")
        String description,

        String startAt,
        String endAt,
        String applicationDeadline,

        @NotBlank(message = "場所を入力してください。")
        @Size(max = 200, message = "場所は200文字以内にしてください。")
        String location,

        @NotNull(message = "定員は1〜1000人の整数にしてください。")
        @Min(value = 1, message = "定員は1〜1000人の整数にしてください。")
        @Max(value = 1000, message = "定員は1〜1000人の整数にしてください。")
        Integer capacity) {

    /**
     * 【何のため】タイトル、説明、場所の前後の空白を取り除く。説明が送られてこなかったときは、空の文字にする。
     */
    public EventInput {
        title = title == null ? null : title.strip();
        description = description == null ? "" : description.strip();
        location = location == null ? null : location.strip();
    }
}
