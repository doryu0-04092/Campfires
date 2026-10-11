package com.campfires.event;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.campfires.common.web.ApiException;
import com.campfires.common.web.FieldErrorItem;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * 【役割】イベントの作成・編集のデータを、まとめてチェックするクラス。
 * 【なぜ必要か】文字数などの項目ごとのルール（EventInput の @Size など）と、
 * 日時の前後関係のルール（今 < 開始日時、締め切り < 開始日時 < 終了日時）を、1回の返事でまとめて返すため。
 * （@Valid だけだと、項目ごとのルールに通った後でないと、日時のルールを確かめられない）
 */
@Component
public class EventInputValidator {

    // Bean Validation（@Size などのルール）を実行する道具。Spring が用意してくれる
    private final Validator validator;

    public EventInputValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * 【何のため】データをチェックし、通ったら ValidEventInput を返す。通らなければ 400（VALIDATION_ERROR）を投げる。
     * @param now     今の日時（開始日時が今より後かを確かめる）
     * @param current 編集のときは、今のイベント（承認の方法が送られてこなければ、今の値を使う）。作成のときは null
     */
    public ValidEventInput validate(EventInput input, Instant now, EventRow current) {
        // 項目の名前 → エラーの文。LinkedHashMap は、入れた順番を覚えておく入れ物。1つの項目に1つの文だけを入れる
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<EventInput> violation : validator.validate(input)) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }

        Instant startAt = parse(input.startAt());
        Instant endAt = parse(input.endAt());
        Instant deadline = parse(input.applicationDeadline());
        if (startAt == null) {
            errors.putIfAbsent("startAt", "開始日時を入力してください。");
        } else if (!startAt.isAfter(now)) {
            errors.putIfAbsent("startAt", "開始日時は、今より後にしてください。");
        }
        if (endAt == null) {
            errors.putIfAbsent("endAt", "終了日時を入力してください。");
        } else if (startAt != null && !endAt.isAfter(startAt)) {
            errors.putIfAbsent("endAt", "終了日時は、開始日時より後にしてください。");
        }
        if (deadline == null) {
            errors.putIfAbsent("applicationDeadline", "申込み締め切り日時を入力してください。");
        } else if (startAt != null && !deadline.isBefore(startAt)) {
            errors.putIfAbsent("applicationDeadline", "申込み締め切り日時は、開始日時より前にしてください。");
        }

        if (!errors.isEmpty()) {
            List<FieldErrorItem> items = new ArrayList<>();
            errors.forEach((field, message) -> items.add(new FieldErrorItem(field, message)));
            throw ApiException.validation(items);
        }
        // 承認の方法が送られてこなければ、作成では最初の値（メンバーは自動、メンバー以外は受け付けない）、編集では今の値
        String memberApproval = input.memberApproval() != null ? input.memberApproval()
                : current != null ? current.memberApproval() : "auto";
        String nonMemberApproval = input.nonMemberApproval() != null ? input.nonMemberApproval()
                : current != null ? current.nonMemberApproval() : "closed";
        return new ValidEventInput(input.title(), input.description(), startAt, endAt, deadline, input.location(),
                input.capacity(), memberApproval, nonMemberApproval);
    }

    /**
     * 【何のため】日時の文字（例：2026-11-01T10:00:00Z、2026-11-01T19:00:00+09:00）を、日時に変える。
     * 空、または正しくない形なら null を返す。
     */
    private static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // OffsetDateTime は「時差付きの日時」。Z（世界標準時）も +09:00 も読める
            return OffsetDateTime.parse(value.strip()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
