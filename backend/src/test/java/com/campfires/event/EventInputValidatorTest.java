package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.campfires.common.web.ApiException;
import com.campfires.common.web.FieldErrorItem;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/**
 * 【役割】イベントの作成・編集のデータをチェックする EventInputValidator の単体テスト。
 * 【なぜ必要か】文字数・定員の境目（100文字と101文字、1人と1001人など）と、日時の前後関係
 * （今 < 開始、締め切り < 開始 < 終了）のルールを、ちょうど同じ日時の場合も含めて、細かく確かめるため。
 * API のテスト（EventApiTest）では、代表的なまちがいだけを確かめている。
 *
 * Spring は起動せず、Bean Validation（@Size などのルール）の道具だけを、自分で作って使う。
 */
class EventInputValidatorTest {

    // 「今」の日時をテストで決めておく（いつテストを動かしても、同じ結果になるように）
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    // Bean Validation の道具を作る工場。テストの最初に1回だけ作り、最後に閉じる
    private static ValidatorFactory factory;
    private static EventInputValidator validator;

    /** 【何のため】@BeforeAll は「このクラスのテストを始める前に1回だけ動かす」という印。 */
    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = new EventInputValidator(factory.getValidator());
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    /** 【何のため】正しいイベントの入力を作る（開始は今の2日後、締め切りは1日後、終了は開始の2時間後）。 */
    private static EventInput valid() {
        return input("もくもく会", 10, "2026-11-03T10:00:00Z", "2026-11-03T12:00:00Z", "2026-11-02T00:00:00Z");
    }

    private static EventInput input(String title, Integer capacity, String startAt, String endAt, String deadline) {
        return new EventInput(title, "説明", startAt, endAt, deadline, "オンライン", capacity, null, null);
    }

    /** 【何のため】チェックに通らないことを確かめ、項目ごとのエラーの一覧を返す。 */
    private static List<FieldErrorItem> errorsOf(EventInput input) {
        // catchThrowableOfType は「投げられた例外を、指定した型で受け取る」AssertJ の道具
        ApiException exception = catchThrowableOfType(ApiException.class,
                () -> validator.validate(input, NOW, null));
        assertThat(exception).as("チェックに通らないはず").isNotNull();
        assertThat(exception.getCode()).isEqualTo("VALIDATION_ERROR");
        return exception.getFieldErrors();
    }

    /** 【何のため】エラーの一覧から、項目の名前だけを取り出す。 */
    private static List<String> fieldsOf(EventInput input) {
        return errorsOf(input).stream().map(FieldErrorItem::field).toList();
    }

    /**
     * 【何のため】正しい入力なら、日時が Instant に変わり、承認の方法が最初の値（自動・受け付けない）になることを確かめる。
     */
    @Test
    void 正しい入力は日時に変えて返す() {
        ValidEventInput result = validator.validate(valid(), NOW, null);

        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-11-03T12:00:00Z"));
        assertThat(result.applicationDeadline()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
        assertThat(result.memberApproval()).isEqualTo("auto");
        assertThat(result.nonMemberApproval()).isEqualTo("closed");
    }

    /**
     * 【何のため】時差付きの日時（+09:00）も読めること、前後の空白を無視することを確かめる。
     */
    @Test
    void 時差付きの日時も読める() {
        EventInput input = input("もくもく会", 10, " 2026-11-03T19:00:00+09:00 ", "2026-11-03T21:00:00+09:00",
                "2026-11-02T09:00:00+09:00");

        ValidEventInput result = validator.validate(input, NOW, null);

        // 日本時間の19時は、世界標準時の10時
        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
    }

    /**
     * 【何のため】タイトルの文字数の境目（100文字は通す、101文字は通さない）と、空白だけのタイトルを確かめる。
     * 前後の空白は取り除いてから数えるので、空白を足しても100文字なら通す。
     */
    @Test
    void タイトルは1文字から100文字まで() {
        assertThat(validator.validate(input("あ".repeat(100), 10, "2026-11-03T10:00:00Z", "2026-11-03T12:00:00Z",
                "2026-11-02T00:00:00Z"), NOW, null).title()).hasSize(100);
        assertThat(validator.validate(input("  " + "あ".repeat(100) + "  ", 10, "2026-11-03T10:00:00Z",
                "2026-11-03T12:00:00Z", "2026-11-02T00:00:00Z"), NOW, null).title()).hasSize(100);

        assertThat(fieldsOf(input("あ".repeat(101), 10, "2026-11-03T10:00:00Z", "2026-11-03T12:00:00Z",
                "2026-11-02T00:00:00Z"))).containsExactly("title");
        assertThat(fieldsOf(input(" 　 ", 10, "2026-11-03T10:00:00Z", "2026-11-03T12:00:00Z",
                "2026-11-02T00:00:00Z"))).containsExactly("title");
        assertThat(fieldsOf(input(null, 10, "2026-11-03T10:00:00Z", "2026-11-03T12:00:00Z",
                "2026-11-02T00:00:00Z"))).containsExactly("title");
    }

    /**
     * 【何のため】定員の境目（1人と1000人は通す、0人と1001人と空は通さない）を確かめる。
     */
    @Test
    void 定員は1人から1000人まで() {
        String start = "2026-11-03T10:00:00Z";
        String end = "2026-11-03T12:00:00Z";
        String deadline = "2026-11-02T00:00:00Z";

        assertThat(validator.validate(input("t", 1, start, end, deadline), NOW, null).capacity()).isEqualTo(1);
        assertThat(validator.validate(input("t", 1000, start, end, deadline), NOW, null).capacity()).isEqualTo(1000);
        assertThat(fieldsOf(input("t", 0, start, end, deadline))).containsExactly("capacity");
        assertThat(fieldsOf(input("t", 1001, start, end, deadline))).containsExactly("capacity");
        assertThat(fieldsOf(input("t", null, start, end, deadline))).containsExactly("capacity");
    }

    /**
     * 【何のため】説明は5000文字まで、場所は200文字まで（空の場所は通さない）ことを確かめる。
     * 説明が送られてこない（null）ときは、空の文字として扱う。
     */
    @Test
    void 説明は5000文字まで場所は200文字まで() {
        String start = "2026-11-03T10:00:00Z";
        String end = "2026-11-03T12:00:00Z";
        String deadline = "2026-11-02T00:00:00Z";

        EventInput okInput = new EventInput("t", "あ".repeat(5000), start, end, deadline, "い".repeat(200), 10, null, null);
        assertThat(validator.validate(okInput, NOW, null).description()).hasSize(5000);
        EventInput noDescription = new EventInput("t", null, start, end, deadline, "場所", 10, null, null);
        assertThat(validator.validate(noDescription, NOW, null).description()).isEmpty();

        EventInput tooLong = new EventInput("t", "あ".repeat(5001), start, end, deadline, "い".repeat(201), 10, null, null);
        assertThat(fieldsOf(tooLong)).containsExactlyInAnyOrder("description", "location");
        EventInput blankLocation = new EventInput("t", "", start, end, deadline, "  ", 10, null, null);
        assertThat(fieldsOf(blankLocation)).containsExactly("location");
    }

    /**
     * 【何のため】開始日時が「今」ちょうどなら通さず、1秒後なら通すことを確かめる（「今より後」のルール）。
     */
    @Test
    void 開始日時は今より後() {
        // 締め切りは開始より前でなければならないので、今より前の締め切りにする
        assertThat(errorsOf(input("t", 10, "2026-11-01T00:00:00Z", "2026-11-01T02:00:00Z", "2026-10-31T00:00:00Z")))
                .containsExactly(new FieldErrorItem("startAt", "開始日時は、今より後にしてください。"));
        assertThat(validator.validate(
                input("t", 10, "2026-11-01T00:00:01Z", "2026-11-01T02:00:00Z", "2026-10-31T00:00:00Z"), NOW, null)
                .startAt()).isEqualTo(Instant.parse("2026-11-01T00:00:01Z"));
    }

    /**
     * 【何のため】終了日時が開始日時と同じなら通さないこと、締め切りが開始日時と同じなら通さないことを確かめる。
     */
    @Test
    void 終了は開始より後で締め切りは開始より前() {
        String start = "2026-11-03T10:00:00Z";

        assertThat(errorsOf(input("t", 10, start, start, "2026-11-02T00:00:00Z")))
                .containsExactly(new FieldErrorItem("endAt", "終了日時は、開始日時より後にしてください。"));
        assertThat(errorsOf(input("t", 10, start, "2026-11-03T12:00:00Z", start)))
                .containsExactly(new FieldErrorItem("applicationDeadline", "申込み締め切り日時は、開始日時より前にしてください。"));
        // 締め切りが「今」より前でも、開始より前ならルールには合う（作成の画面では、締め切りを過ぎたイベントになる）
        assertThat(validator.validate(input("t", 10, start, "2026-11-03T12:00:00Z", "2026-10-01T00:00:00Z"), NOW, null))
                .isNotNull();
    }

    /**
     * 【何のため】日時が空・形が正しくない（時差がない、存在しない日）ときは、「入力してください」のエラーになることを確かめる。
     * 開始日時が読めないときは、終了と締め切りの前後関係は確かめない（読めないエラーだけを返す）。
     */
    @Test
    void 日時が読めなければ入力のエラー() {
        List<FieldErrorItem> errors = errorsOf(input("t", 10, "2026-11-03T10:00:00", "", "2026-02-30T00:00:00Z"));

        assertThat(errors).containsExactly(
                new FieldErrorItem("startAt", "開始日時を入力してください。"),
                new FieldErrorItem("endAt", "終了日時を入力してください。"),
                new FieldErrorItem("applicationDeadline", "申込み締め切り日時を入力してください。"));

        // 開始日時だけが読めないとき、終了と締め切りは正しい形なので、エラーにしない
        assertThat(fieldsOf(input("t", 10, null, "2026-11-03T12:00:00Z", "2026-11-04T00:00:00Z")))
                .containsExactly("startAt");
    }

    /**
     * 【何のため】たくさんの項目がまちがっているとき、1回の返事で全部のエラーを返し、1つの項目には1つの文だけを入れることを確かめる。
     */
    @Test
    void まちがいを1回でまとめて返す() {
        EventInput input = new EventInput("", "", "2026-10-01T00:00:00Z", "2026-09-01T00:00:00Z",
                "2026-12-01T00:00:00Z", "", 0, "closed", "everyone");

        assertThat(fieldsOf(input)).containsExactlyInAnyOrder("title", "location", "capacity", "memberApproval",
                "nonMemberApproval", "startAt", "endAt", "applicationDeadline");
    }

    /**
     * 【何のため】承認の方法が送られてこないとき、作成では最初の値、編集では今の値を使うことを確かめる。
     * 送られてきたときは、その値を使う。
     */
    @Test
    void 承認の方法が送られてこなければ今の値を使う() {
        EventRow current = EventRowFixtures.row(10, 0, "manual", "auto");
        EventInput notSent = valid();
        EventInput sent = new EventInput("t", "", "2026-11-03T10:00:00Z", "2026-11-03T12:00:00Z",
                "2026-11-02T00:00:00Z", "場所", 10, "auto", "manual");

        ValidEventInput edited = validator.validate(notSent, NOW, current);
        assertThat(edited.memberApproval()).isEqualTo("manual");
        assertThat(edited.nonMemberApproval()).isEqualTo("auto");

        ValidEventInput changed = validator.validate(sent, NOW, current);
        assertThat(changed.memberApproval()).isEqualTo("auto");
        assertThat(changed.nonMemberApproval()).isEqualTo("manual");
    }
}
