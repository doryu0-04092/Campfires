package com.campfires.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.campfires.MapperTestBase;

/**
 * 【役割】お知らせの SQL（NotificationMapper.xml）を、本物の PostgreSQL で直接確かめるテスト。
 * 【なぜ必要か】「だれにお知らせを送るか」（オーナーと運営スタッフだけ、操作した本人は除く、却下された人は除く）と、
 * 「自分あてのお知らせだけを、見たり既読にしたりできる」ことは、SQL の条件で決まる。
 * 未読の件数の数え方や、既読の日時を上書きしないことも含めて確かめる（要件定義書 5.10）。
 */
class NotificationMapperSqlTest extends MapperTestBase {

    @Autowired
    private NotificationMapper notificationMapper;

    /** 【何のため】ユーザーあてのお知らせの種類を、番号の順に返す。 */
    private List<String> typesOf(long userId) {
        return jdbcTemplate.queryForList("SELECT type FROM notifications WHERE user_id = ? ORDER BY id", String.class,
                userId);
    }

    /** 【何のため】お知らせを1件、SQL で直接作る（作成日時は、何分前かで指定する）。 */
    private long insertNotification(long userId, long communityId, Long actorUserId, int createdMinutesAgo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notifications (user_id, type, event_title, community_id, actor_user_id, created_at)
                VALUES (?, 'late_cancellation', 'タイトル', ?, ?, now() - make_interval(mins => ?))
                RETURNING id
                """, Long.class, userId, communityId, actorUserId, createdMinutesAgo);
    }

    /**
     * 【何のため】締め切り後のキャンセルのお知らせは、オーナーと運営スタッフに送り、操作した本人と一般のメンバーには送らないことを確かめる。
     * キャンセルした人（actor）と、その時点のイベントのタイトルを残す。
     */
    @Test
    void 締め切り後のキャンセルはオーナーと運営スタッフに知らせる() {
        long owner = insertUser("オーナー");
        long staff1 = insertUser("運営スタッフ1");
        long staff2 = insertUser("運営スタッフ2");
        long member = insertUser("メンバー");
        long canceller = insertUser("キャンセルした人");
        long community = insertCommunity(owner);
        insertMember(community, staff1, "staff");
        insertMember(community, staff2, "staff");
        insertMember(community, member, "member");
        long event = insertEvent(community, "元のタイトル", "", 10, -60, 1440, 1500, 0);

        // 運営スタッフ1が除名の操作をした、という場面
        notificationMapper.insertLateCancellation(event, canceller, staff1);

        assertThat(typesOf(owner)).containsExactly("late_cancellation");
        assertThat(typesOf(staff2)).containsExactly("late_cancellation");
        assertThat(typesOf(staff1)).isEmpty();
        assertThat(typesOf(member)).isEmpty();
        assertThat(typesOf(canceller)).isEmpty();
        NotificationRow row = notificationMapper.findByUser(owner, 30, 0).get(0);
        assertThat(row.actorUserId()).isEqualTo(canceller);
        assertThat(row.actorNickname()).isEqualTo("キャンセルした人");
        assertThat(row.eventTitle()).isEqualTo("元のタイトル");
        assertThat(row.eventId()).isEqualTo(event);
    }

    /**
     * 【何のため】イベントの削除のお知らせは、申し込んでいた人（承認待ち・キャンセル待ちを含む）に送り、
     * 却下された人と、削除した本人には送らないことを確かめる。イベントの番号と actor は入れない。
     */
    @Test
    void イベントの削除は却下された人と本人以外の申込者に知らせる() {
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long event = insertUpcomingEvent(community, 1);
        long applied = insertUser("申込み済み");
        long waitlisted = insertUser("キャンセル待ち");
        long pending = insertUser("承認待ち");
        long rejected = insertUser("却下");
        insertApplication(event, applied, "applied", 40);
        insertApplication(event, waitlisted, "waitlisted", 30);
        insertApplication(event, pending, "pending", 20);
        insertApplication(event, rejected, "rejected", 10);
        // 削除した本人（オーナー）も申し込んでいた
        insertApplication(event, owner, "waitlisted", 5);

        notificationMapper.insertEventDeleted(event, owner);

        assertThat(typesOf(applied)).containsExactly("event_deleted");
        assertThat(typesOf(waitlisted)).containsExactly("event_deleted");
        assertThat(typesOf(pending)).containsExactly("event_deleted");
        assertThat(typesOf(rejected)).isEmpty();
        assertThat(typesOf(owner)).isEmpty();
        NotificationRow row = notificationMapper.findByUser(applied, 30, 0).get(0);
        assertThat(row.eventId()).isNull();
        assertThat(row.actorUserId()).isNull();
        assertThat(row.actorNickname()).isNull();
    }

    /**
     * 【何のため】承認・却下のお知らせは、申し込んだ本人にだけ送り、承認・却下した人の名前（actor）は入れないことを確かめる。
     */
    @Test
    void 承認と却下のお知らせは本人にだけ送る() {
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long event = insertUpcomingEvent(community, 10);
        long user = insertUser("申込み");

        notificationMapper.insertApplicationDecided(event, user, "application_approved");

        assertThat(typesOf(user)).containsExactly("application_approved");
        assertThat(typesOf(owner)).isEmpty();
        assertThat(notificationMapper.findByUser(user, 30, 0).get(0).actorUserId()).isNull();
    }

    /**
     * 【何のため】一覧は自分あてのお知らせだけを、新しい順（同じ日時なら番号の大きい順）に返し、ページで分けられることを確かめる。
     */
    @Test
    void 一覧は自分あてだけを新しい順に返す() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long community = insertCommunity(other);
        long oldest = insertNotification(me, community, null, 30);
        long sameTime1 = insertNotification(me, community, null, 10);
        long sameTime2 = insertNotification(me, community, null, 10);
        insertNotification(other, community, null, 0);

        assertThat(notificationMapper.countByUser(me)).isEqualTo(3);
        assertThat(notificationMapper.findByUser(me, 30, 0)).extracting(NotificationRow::id)
                .containsExactly(sameTime2, sameTime1, oldest);
        assertThat(notificationMapper.findByUser(me, 2, 2)).extracting(NotificationRow::id).containsExactly(oldest);
    }

    /**
     * 【何のため】未読の件数は自分あての未読だけを数え、ほかの人のお知らせは既読にできない（書き換わらない）ことを確かめる。
     */
    @Test
    void 未読の件数と既読は自分あてだけ() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long community = insertCommunity(other);
        long mine = insertNotification(me, community, null, 3);
        insertNotification(me, community, null, 2);
        long others = insertNotification(other, community, null, 1);

        assertThat(notificationMapper.countUnread(me)).isEqualTo(2);

        // ほかの人のお知らせを、自分の番号で既読にしようとしても、何も変わらない
        notificationMapper.markRead(me, others);
        assertThat(notificationMapper.countUnread(other)).isEqualTo(1);
        assertThat(notificationMapper.findOne(me, others)).isNull();

        notificationMapper.markRead(me, mine);
        assertThat(notificationMapper.countUnread(me)).isEqualTo(1);

        notificationMapper.markAllRead(me);
        assertThat(notificationMapper.countUnread(me)).isZero();
        assertThat(notificationMapper.countUnread(other)).isEqualTo(1);
    }

    /**
     * 【何のため】すでに既読のお知らせを、もう一度既読にしても、既読にした日時を上書きしないことを確かめる。
     * すべて既読にしたときも、もとから既読のお知らせの日時は変えない。
     */
    @Test
    void 既読の日時は上書きしない() {
        long me = insertUser("わたし");
        long community = insertCommunity(me);
        long read = insertNotification(me, community, null, 10);
        jdbcTemplate.update("UPDATE notifications SET read_at = now() - interval '1 day' WHERE id = ?", read);
        Instant firstReadAt = notificationMapper.findOne(me, read).readAt();

        notificationMapper.markRead(me, read);
        notificationMapper.markAllRead(me);

        assertThat(notificationMapper.findOne(me, read).readAt()).isEqualTo(firstReadAt);
    }

    /**
     * 【何のため】出来事を起こした人がアカウントを削除していても（ニックネームが空）、お知らせは一覧に残ることを確かめる。
     */
    @Test
    void 起こした人が削除済みでもお知らせは残る() {
        long me = insertUser("わたし");
        long actor = insertUser("キャンセルした人");
        long community = insertCommunity(me);
        insertNotification(me, community, actor, 1);
        jdbcTemplate.update("""
                UPDATE users SET real_name = NULL, nickname = NULL, email = NULL, password_hash = NULL,
                                 deleted_at = now()
                WHERE id = ?
                """, actor);

        NotificationRow row = notificationMapper.findByUser(me, 30, 0).get(0);

        assertThat(row.actorUserId()).isEqualTo(actor);
        assertThat(row.actorNickname()).isNull();
    }
}
