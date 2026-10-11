package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.campfires.MapperTestBase;

/**
 * 【役割】申込みの SQL（ApplicationMapper.xml）を、本物の PostgreSQL で直接確かめるテスト。
 * 【なぜ必要か】キャンセル待ちの順番（queued_at ＝ 席の順番を取った日時で決める）、申込者の一覧の並び順、
 * 繰り上げる人数の計算（定員 − 申込み済み、締め切り後は繰り上げない）、承認・却下の「承認待ちのときだけ」の条件は、
 * どれも SQL の中に書いたルール。API のテストでは通りにくい組み合わせ（同じ日時、定員を超えた状態など）も確かめる。
 */
class ApplicationMapperSqlTest extends MapperTestBase {

    @Autowired
    private ApplicationMapper applicationMapper;

    /** 【何のため】申込みの席の順番（queued_at）が入っているかを返す。 */
    private boolean hasQueuedAt(long eventId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT queued_at IS NOT NULL FROM applications WHERE event_id = ? AND user_id = ?", Boolean.class,
                eventId, userId);
    }

    // ---------- 登録と自分の申込み ----------

    /**
     * 【何のため】承認待ちは席の順番を取らず（queued_at が空）、申込み済み・キャンセル待ちは取ることを確かめる。
     * 同じ人がもう一度申し込むと、データベースの制約で DuplicateKeyException になる（最後に確かめる。
     * 制約で止まると、このトランザクションの中では、それ以降の SQL を実行できなくなるため）。
     */
    @Test
    void 承認待ちは席の順番を取らず二重の申込みは止まる() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 10);
        long pending = insertUser("承認待ち");
        long applied = insertUser("申込み済み");

        applicationMapper.insert(event, pending, "pending");
        applicationMapper.insert(event, applied, "applied");

        assertThat(hasQueuedAt(event, pending)).isFalse();
        assertThat(hasQueuedAt(event, applied)).isTrue();
        assertThat(applicationMapper.findMyApplication(event, pending).status()).isEqualTo("pending");
        assertThat(applicationMapper.findMyApplication(event, insertUser("申し込んでいない"))).isNull();
        assertThatThrownBy(() -> applicationMapper.insert(event, applied, "waitlisted"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /**
     * 【何のため】キャンセル待ちの順番は、申し込んだ日時ではなく、席の順番を取った日時（承認された日時）で決まることを確かめる。
     * 先に申し込んでも、後から承認された人は、後ろに並ぶ。
     */
    @Test
    void キャンセル待ちの順番は席の順番を取った日時で決まる() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 1);
        long early = insertUser("先に申し込んだが後で承認された");
        long late = insertUser("後で申し込んだ");
        insertApplication(event, insertUser("申込み済み"), "applied", 100, 100);
        insertApplication(event, early, "waitlisted", 60, 10);
        insertApplication(event, late, "waitlisted", 30, 30);

        assertThat(applicationMapper.findMyApplication(event, late).waitlistPosition()).isEqualTo(1);
        assertThat(applicationMapper.findMyApplication(event, early).waitlistPosition()).isEqualTo(2);
        assertThat(applicationMapper.findMyApplication(event, early).appliedAt())
                .isBefore(applicationMapper.findMyApplication(event, late).appliedAt());
    }

    /**
     * 【何のため】席の順番を取った日時がまったく同じなら、申込みの番号の小さい順に並ぶことを確かめる。
     */
    @Test
    void 同じ日時なら番号の小さい順() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 1);
        long first = insertUser("先");
        long second = insertUser("後");
        insertApplication(event, first, "waitlisted", 5, 5);
        insertApplication(event, second, "waitlisted", 5, 5);

        assertThat(applicationMapper.findMyApplication(event, first).waitlistPosition()).isEqualTo(1);
        assertThat(applicationMapper.findMyApplication(event, second).waitlistPosition()).isEqualTo(2);
    }

    // ---------- 申込者の一覧 ----------

    /**
     * 【何のため】申込者の一覧の並び順を確かめる。
     * 承認待ち（申し込んだ順）→ 申込み済み（席の順番どおり）→ キャンセル待ち（順番どおり）。却下は出さない。
     * ページを分けても、キャンセル待ちの順番は、全体の中での順番のまま。
     */
    @Test
    void 申込者の一覧は承認待ち申込み済みキャンセル待ちの順() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 2);
        long p1 = insertUser("承認待ち1");
        long p2 = insertUser("承認待ち2");
        long a1 = insertUser("申込み済み1");
        long a2 = insertUser("申込み済み2");
        long w1 = insertUser("キャンセル待ち1");
        long w2 = insertUser("キャンセル待ち2");
        insertApplication(event, p2, "pending", 40, null);
        insertApplication(event, p1, "pending", 50, null);
        insertApplication(event, a2, "applied", 60, 35);
        insertApplication(event, a1, "applied", 70, 30);
        insertApplication(event, w1, "waitlisted", 80, 20);
        insertApplication(event, w2, "waitlisted", 90, 25);
        insertApplication(event, insertUser("却下"), "rejected", 100, null);

        assertThat(applicationMapper.countApplications(event)).isEqualTo(6);
        List<ApplicationRow> all = applicationMapper.findApplications(event, 30, 0);
        assertThat(all).extracting(ApplicationRow::userId).containsExactly(p1, p2, a2, a1, w2, w1);
        assertThat(all).extracting(ApplicationRow::waitlistPosition).containsExactly(null, null, null, null, 1, 2);
        assertThat(all.get(0).nickname()).isEqualTo("承認待ち1");

        // 2件ずつに分けた3ページ目でも、キャンセル待ちの順番は 1、2 のまま
        List<ApplicationRow> page3 = applicationMapper.findApplications(event, 2, 4);
        assertThat(page3).extracting(ApplicationRow::waitlistPosition).containsExactly(1, 2);
    }

    /**
     * 【何のため】1人分の申込みを返すとき、却下された申込みも返し、キャンセル待ちの順番は全員の中で数えることを確かめる。
     */
    @Test
    void 一人分でもキャンセル待ちの順番は全員の中で数える() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 1);
        long rejected = insertUser("却下");
        long second = insertUser("2番目");
        insertApplication(event, insertUser("1番目"), "waitlisted", 20);
        insertApplication(event, second, "waitlisted", 10);
        insertApplication(event, rejected, "rejected", 5);

        assertThat(applicationMapper.findApplication(event, second).waitlistPosition()).isEqualTo(2);
        assertThat(applicationMapper.findApplication(event, rejected).status()).isEqualTo("rejected");
        assertThat(applicationMapper.findApplication(event, insertUser("申し込んでいない"))).isNull();
    }

    // ---------- 承認・却下 ----------

    /**
     * 【何のため】承認待ちの申込みを、メンバーとメンバー以外に分けて、申し込んだ順に返すことを確かめる。
     */
    @Test
    void 承認待ちをメンバーとメンバー以外に分けて申し込んだ順に返す() {
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long event = insertUpcomingEvent(community, 10);
        long member1 = insertUser("メンバー1");
        long member2 = insertUser("メンバー2");
        long outsider = insertUser("メンバー以外");
        insertMember(community, member1, "member");
        insertMember(community, member2, "staff");
        insertApplication(event, member2, "pending", 30);
        insertApplication(event, member1, "pending", 20);
        insertApplication(event, outsider, "pending", 40);
        insertApplication(event, insertUser("申込み済み"), "applied", 50);

        assertThat(applicationMapper.findPending(event, true)).extracting(PendingApplication::userId)
                .containsExactly(member2, member1);
        assertThat(applicationMapper.findPending(event, false)).extracting(PendingApplication::userId)
                .containsExactly(outsider);
    }

    /**
     * 【何のため】承認・却下は、承認待ちのときだけ書き換えることを確かめる（2回目は 0 行）。
     * 承認すると、席の順番（queued_at）を取る。
     */
    @Test
    void 承認と却下は承認待ちのときだけ() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 10);
        long approved = insertUser("承認される");
        long rejected = insertUser("却下される");
        insertApplication(event, approved, "pending", 10);
        insertApplication(event, rejected, "pending", 10);

        assertThat(applicationMapper.approve(event, approved, "applied")).isEqualTo(1);
        assertThat(applicationMapper.approve(event, approved, "applied")).isZero();
        assertThat(applicationMapper.reject(event, approved)).isZero();
        assertThat(hasQueuedAt(event, approved)).isTrue();

        assertThat(applicationMapper.reject(event, rejected)).isEqualTo(1);
        assertThat(applicationMapper.approve(event, rejected, "applied")).isZero();
        assertThat(applicationStatus(event, rejected)).isEqualTo("rejected");
        assertThat(hasQueuedAt(event, rejected)).isFalse();
    }

    // ---------- 繰り上げ ----------

    /**
     * 【何のため】空いている枠の数（定員 − 申込み済み）だけ、キャンセル待ちを席の順番どおりに繰り上げることを確かめる。
     * 空きがなくなったら、もう一度呼んでも何もしない。
     */
    @Test
    void 空いている枠の数だけ順番どおりに繰り上げる() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 3);
        long w1 = insertUser("2番目");
        long w2 = insertUser("1番目");
        long w3 = insertUser("3番目");
        insertApplication(event, insertUser("申込み済み"), "applied", 100);
        insertApplication(event, w1, "waitlisted", 50);
        insertApplication(event, w2, "waitlisted", 60);
        insertApplication(event, w3, "waitlisted", 40);
        insertApplication(event, insertUser("承認待ち"), "pending", 90);

        applicationMapper.promoteWaitlist(event);

        assertThat(applicationStatus(event, w2)).isEqualTo("applied");
        assertThat(applicationStatus(event, w1)).isEqualTo("applied");
        assertThat(applicationStatus(event, w3)).isEqualTo("waitlisted");

        applicationMapper.promoteWaitlist(event);
        assertThat(applicationStatus(event, w3)).isEqualTo("waitlisted");
        assertThat(applicationMapper.findMyApplication(event, w3).waitlistPosition()).isEqualTo(1);
    }

    /**
     * 【何のため】締め切りを過ぎたイベントでは、空きがあっても繰り上げないことを確かめる（要件定義書 5.2）。
     */
    @Test
    void 締め切り後は繰り上げない() {
        long event = insertDeadlinePassedEvent(insertCommunity(insertUser("オーナー")), 5);
        long waiting = insertUser("キャンセル待ち");
        insertApplication(event, waiting, "waitlisted", 10);

        applicationMapper.promoteWaitlist(event);

        assertThat(applicationStatus(event, waiting)).isEqualTo("waitlisted");
    }

    /**
     * 【何のため】申込み済みが定員を超えている状態（定員を後から減らすことはできないが、念のため）でも、
     * 繰り上げる人数がマイナスにならず、エラーにもならないことを確かめる（GREATEST で 0 以上にしている）。
     */
    @Test
    void 定員を超えていても繰り上げの人数はマイナスにならない() {
        long event = insertUpcomingEvent(insertCommunity(insertUser("オーナー")), 1);
        long waiting = insertUser("キャンセル待ち");
        insertApplication(event, insertUser("a"), "applied", 30);
        insertApplication(event, insertUser("b"), "applied", 20);
        insertApplication(event, waiting, "waitlisted", 10);

        applicationMapper.promoteWaitlist(event);

        assertThat(applicationStatus(event, waiting)).isEqualTo("waitlisted");
    }

    // ---------- 取り消し ----------

    /**
     * 【何のため】取り消す申込みを探すとき、却下された申込みは含めず、締め切りを過ぎたかどうかが入ることを確かめる。
     */
    @Test
    void 却下された申込みは取り消しの対象にしない() {
        long community = insertCommunity(insertUser("オーナー"));
        long open = insertUpcomingEvent(community, 10);
        long closed = insertDeadlinePassedEvent(community, 10);
        long user = insertUser("申込み");
        long rejected = insertUser("却下");
        insertApplication(open, user, "applied", 10);
        insertApplication(closed, user, "waitlisted", 10);
        insertApplication(open, rejected, "rejected", 10);

        assertThat(applicationMapper.findCancelTarget(open, user).deadlinePassed()).isFalse();
        assertThat(applicationMapper.findCancelTarget(closed, user).deadlinePassed()).isTrue();
        assertThat(applicationMapper.findCancelTarget(closed, user).status()).isEqualTo("waitlisted");
        assertThat(applicationMapper.findCancelTarget(open, rejected)).isNull();
    }

    /**
     * 【何のため】ユーザーが申し込んでいる、終了していないイベントの番号を、番号の小さい順に返すことを確かめる。
     * コミュニティを指定したときは、そのコミュニティのイベントだけ。
     */
    @Test
    void 終了していないイベントの番号を小さい順に返す() {
        long owner = insertUser("オーナー");
        long communityA = insertCommunity(owner);
        long communityB = insertCommunity(owner);
        long user = insertUser("申込み");
        long a1 = insertUpcomingEvent(communityA, 10);
        long b1 = insertUpcomingEvent(communityB, 10);
        long a2 = insertDeadlinePassedEvent(communityA, 10);
        long ended = insertEndedEvent(communityA);
        insertApplication(a2, user, "applied", 10);
        insertApplication(b1, user, "pending", 10);
        insertApplication(a1, user, "waitlisted", 10);
        insertApplication(ended, user, "applied", 10);

        assertThat(applicationMapper.findUpcomingEventIdsOfUser(user, null)).containsExactly(a1, b1, a2);
        assertThat(applicationMapper.findUpcomingEventIdsOfUser(user, communityA)).containsExactly(a1, a2);
    }
}
