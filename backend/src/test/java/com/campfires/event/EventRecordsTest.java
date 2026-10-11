package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 【役割】イベントと申込みの、小さなデータのクラス（EventRow、CancelTarget、EventDetail、MyEventRow、ApplicationRow、EventInput）
 * に書いた、判断と変換の処理の単体テスト。
 * 【なぜ必要か】「メンバーかどうかで、使う承認の方法が変わる」「残りの枠数＝定員 − 申込み済み」などの小さなルールは、
 * まちがえると申込みや表示がずれる。データベースを使わずに、すばやく確かめる。
 */
class EventRecordsTest {

    /**
     * 【何のため】メンバーなら memberApproval、メンバー以外なら nonMemberApproval を使うことを確かめる。
     */
    @Test
    void メンバーかどうかで承認の方法を選ぶ() {
        EventRow row = EventRowFixtures.row(10, 0, "manual", "closed");

        assertThat(row.approvalFor(true)).isEqualTo("manual");
        assertThat(row.approvalFor(false)).isEqualTo("closed");
    }

    /**
     * 【何のため】取り消す申込みの状態の判断（申込み済みか、承認待ちか）を確かめる。キャンセル待ちはどちらでもない。
     */
    @Test
    void 取り消す申込みの状態を判断する() {
        assertThat(new CancelTarget(1, 2, 3, "applied", false).isApplied()).isTrue();
        assertThat(new CancelTarget(1, 2, 3, "applied", false).isPending()).isFalse();
        assertThat(new CancelTarget(1, 2, 3, "pending", false).isPending()).isTrue();
        assertThat(new CancelTarget(1, 2, 3, "waitlisted", false).isApplied()).isFalse();
        assertThat(new CancelTarget(1, 2, 3, "waitlisted", false).isPending()).isFalse();
    }

    /**
     * 【何のため】イベントの詳細の残りの枠数が「定員 − 申込み済みの人数」になり、見ている人の情報が入ることを確かめる。
     */
    @Test
    void 詳細の残りの枠数は定員から申込み済みを引く() {
        MyApplication mine = new MyApplication("waitlisted", 2, Instant.parse("2026-10-02T00:00:00Z"));

        EventDetail detail = EventDetail.of(EventRowFixtures.row(10, 10, "auto", "closed"), "member", mine);

        assertThat(detail.remainingSeats()).isZero();
        assertThat(detail.myRole()).isEqualTo("member");
        assertThat(detail.myApplication()).isEqualTo(mine);
        assertThat(EventDetail.of(EventRowFixtures.row(10, 3, "auto", "closed"), null, null).remainingSeats())
                .isEqualTo(7);
    }

    /**
     * 【何のため】自分が申し込んだイベントの行を、返事の形に変えると、申込みの状況が myApplication にまとまることを確かめる。
     */
    @Test
    void 自分のイベントの行を返事の形に変える() {
        Instant appliedAt = Instant.parse("2026-10-02T00:00:00Z");
        MyEventRow row = new MyEventRow(1, 2, "コミュニティ", "タイトル", appliedAt, appliedAt, 10, 3, "waitlisted", 4,
                appliedAt);

        MyEvent response = row.toResponse();

        assertThat(response.myApplication()).isEqualTo(new MyApplication("waitlisted", 4, appliedAt));
        assertThat(response.remainingSeats()).isEqualTo(3);
    }

    /**
     * 【何のため】申込者の行を返事の形に変えると、申し込んだ人が UserRef（番号とニックネーム）になることを確かめる。
     * アカウントを削除した人は、ニックネームが null のまま（画面で「退会したユーザー」と表示する）。
     */
    @Test
    void 申込者の行を返事の形に変える() {
        ApplicationResponse response = new ApplicationRow(5, null, "applied", null, Instant.EPOCH).toResponse();

        assertThat(response.user().userId()).isEqualTo(5);
        assertThat(response.user().nickname()).isNull();
        assertThat(response.status()).isEqualTo("applied");
    }

    /**
     * 【何のため】イベントの入力の前後の空白を取り除き、説明が送られてこなければ空の文字にすることを確かめる。
     */
    @Test
    void 入力の前後の空白を取り除く() {
        EventInput input = new EventInput("  タイトル ", null, null, null, null, "　場所　", 1, null, null);

        assertThat(input.title()).isEqualTo("タイトル");
        assertThat(input.description()).isEmpty();
        assertThat(input.location()).isEqualTo("場所");
    }
}
