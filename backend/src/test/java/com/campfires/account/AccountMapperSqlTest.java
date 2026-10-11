package com.campfires.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.campfires.MapperTestBase;

/**
 * 【役割】アカウントとユーザーの SQL（AccountMapper.xml、UserMapper.xml）を、本物の PostgreSQL で直接確かめるテスト。
 * 【なぜ必要か】アカウントの削除（要件定義書 5.5）の「削除できる条件」と「削除したときの後片付け」
 * （オーナー以外の立場から退会、申請中の参加申請の却下、個人情報の消去）は、SQL で行う。
 * 削除したユーザーと同じメールアドレスで、もう一度登録できること（部分インデックス）も確かめる。
 */
class AccountMapperSqlTest extends MapperTestBase {

    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private UserMapper userMapper;

    /** 【何のため】毎回ちがうメールアドレスを作る。 */
    private static String email() {
        return "account-" + UUID.randomUUID() + "@example.com";
    }

    // ---------- 削除できる条件 ----------

    /**
     * 【何のため】オーナーをしている「アーカイブしていない」コミュニティだけを数えることを確かめる。
     */
    @Test
    void アーカイブしていないオーナーのコミュニティだけを数える() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        insertCommunity(me, "アーカイブしていない", false, 0);
        insertCommunity(me, "アーカイブ中", true, 0);
        long staffed = insertCommunity(other);
        insertMember(staffed, me, "staff");

        assertThat(accountMapper.countActiveOwnedCommunities(me)).isEqualTo(1);
        assertThat(accountMapper.countActiveOwnedCommunities(other)).isEqualTo(1);
    }

    /**
     * 【何のため】アーカイブ中でも、オーナーのコミュニティに開催前のイベントがあれば true、
     * 終了したイベントだけなら false、運営スタッフをしているコミュニティのイベントは数えないことを確かめる。
     */
    @Test
    void オーナーのコミュニティの開催前のイベントだけを見る() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long archived = insertCommunity(me, "アーカイブ中", true, 0);
        insertEndedEvent(archived);
        long staffed = insertCommunity(other);
        insertMember(staffed, me, "staff");
        insertUpcomingEvent(staffed, 10);

        assertThat(accountMapper.existsUpcomingEventInOwnedCommunities(me)).isFalse();
    }

    /**
     * 【何のため】開始していて、まだ終わっていないイベント（開催中）も「開催前」として数える（終了日時が今より後）ことを確かめる。
     * 注意：同じテストの中で、同じ SQL を同じ値でもう一度呼ぶと、MyBatis が覚えておいた前の結果（キャッシュ）を返すことがある。
     * そのため、データを変えた後に同じ SQL で確かめたいときは、テストを分ける。
     */
    @Test
    void 開催中のイベントがあれば削除できない状態() {
        long me = insertUser("わたし");
        long archived = insertCommunity(me, "アーカイブ中", true, 0);
        insertEvent(archived, "開催中", "", 10, -120, -60, 60, 0);

        assertThat(accountMapper.existsUpcomingEventInOwnedCommunities(me)).isTrue();
    }

    // ---------- 削除したときの後片付け ----------

    /**
     * 【何のため】退会の SQL は、オーナー以外の立場（運営スタッフ、メンバー）だけを消し、オーナーの行は残すことを確かめる。
     * ほかの人のメンバーの行は消さない。
     */
    @Test
    void オーナー以外の立場から退会する() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long owned = insertCommunity(me, "オーナー", true, 0);
        long staffed = insertCommunity(other);
        long joined = insertCommunity(other);
        insertMember(staffed, me, "staff");
        insertMember(joined, me, "member");
        insertMember(owned, other, "member");

        accountMapper.deleteMembershipsExceptOwner(me);

        assertThat(jdbcTemplate.queryForList("SELECT community_id FROM memberships WHERE user_id = ?", Long.class, me))
                .containsExactly(owned);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM memberships WHERE user_id = ?", Long.class,
                other)).isEqualTo(3);
    }

    /**
     * 【何のため】自分の「申請中」の参加申請だけを却下にし（処理した日時も入れる）、承認済みやほかの人の申請は変えないことを確かめる。
     */
    @Test
    void 自分の申請中の参加申請だけを却下する() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long community = insertCommunity(insertUser("オーナー"));
        long anotherCommunity = insertCommunity(insertUser("オーナー2"));
        jdbcTemplate.update("INSERT INTO join_requests (community_id, user_id, status) VALUES (?, ?, 'pending')",
                community, me);
        jdbcTemplate.update("""
                INSERT INTO join_requests (community_id, user_id, status, decided_at) VALUES (?, ?, 'approved', now())
                """, anotherCommunity, me);
        jdbcTemplate.update("INSERT INTO join_requests (community_id, user_id, status) VALUES (?, ?, 'pending')",
                community, other);

        accountMapper.rejectPendingJoinRequests(me);

        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM join_requests WHERE user_id = ? ORDER BY community_id", String.class, me))
                .containsExactly("rejected", "approved");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM join_requests WHERE user_id = ? AND status = 'rejected' AND decided_at IS NULL",
                Long.class, me)).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT status FROM join_requests WHERE user_id = ?", String.class, other))
                .containsExactly("pending");
    }

    // ---------- ユーザー ----------

    /**
     * 【何のため】メールアドレスは大文字・小文字を区別せずに探せること、削除したユーザーは見つからないことを確かめる。
     * 削除すると、本名・ニックネーム・メールアドレス・パスワードが空になり、行は残る。
     */
    @Test
    void 削除すると個人情報が消えて見つからなくなる() {
        String email = email();
        User created = userMapper.insert("山田 太郎", "たろう", email, "hash", "2026-10-11");

        assertThat(userMapper.findActiveByEmail(email.toUpperCase()).id()).isEqualTo(created.id());

        userMapper.markDeleted(created.id());

        User deleted = userMapper.findById(created.id());
        assertThat(deleted.deletedAt()).isNotNull();
        assertThat(List.of(String.valueOf(deleted.realName()), String.valueOf(deleted.nickname()),
                String.valueOf(deleted.email()), String.valueOf(deleted.passwordHash())))
                .containsOnly("null");
        assertThat(userMapper.findActiveByEmail(email)).isNull();
    }

    /**
     * 【何のため】削除したユーザーのニックネームは、変更の SQL を呼んでも変わらない（空のまま）ことを確かめる。
     */
    @Test
    void 削除したユーザーのニックネームは変えられない() {
        User created = userMapper.insert("山田 太郎", "たろう", email(), "hash", "2026-10-11");
        userMapper.markDeleted(created.id());

        userMapper.updateNickname(created.id(), "じろう");

        assertThat(userMapper.findById(created.id()).nickname()).isNull();
    }

    /**
     * 【何のため】削除したユーザーと同じメールアドレスで、もう一度登録できることを確かめる（部分インデックス）。
     * 削除していないユーザーと同じメールアドレス（大文字・小文字だけが違うものも）は、登録できない。
     * 制約で止まると、このトランザクションの中では、それ以降の SQL を実行できないので、最後に確かめる。
     */
    @Test
    void 削除したユーザーのメールアドレスでもう一度登録できる() {
        String email = email();
        User first = userMapper.insert("山田 太郎", "たろう", email, "hash", "2026-10-11");
        userMapper.markDeleted(first.id());

        User second = userMapper.insert("山田 次郎", "じろう", email, "hash", "2026-10-11");
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(userMapper.findActiveByEmail(email).id()).isEqualTo(second.id());

        assertThatThrownBy(() -> userMapper.insert("山田 三郎", "さぶろう", email.toUpperCase(), "hash", "2026-10-11"))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
