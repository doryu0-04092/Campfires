package com.campfires;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 【役割】Mapper（SQL）のテストで共通に使う道具をまとめた、テストの土台のクラス。
 * 【なぜ必要か】検索・並び順・人数の数え方・繰り上げなどの SQL を、API を通さずに、本物の PostgreSQL で
 * 直接確かめるため。テストのデータを SQL で作る道具を、ここにまとめる。
 *
 * - @Import(TestcontainersConfiguration.class) と @SpringBootTest で、Docker の本物の PostgreSQL を使う。
 *   Flyway のマイグレーション（V1〜V3）も、起動のときに実行される。
 *   BackendApplicationTests と同じ設定なので、Spring の起動は1回で済む（テストどうしで使い回される）。
 * - @Transactional を付けると、テストが終わったときに、テストで作ったデータが自動で取り消される（ロールバック）。
 *   そのため、ほかのテストのデータと混ざりにくい。
 *   注意：1つのトランザクションの中では、PostgreSQL の now() は「トランザクションを始めた日時」のまま変わらない。
 *   テストの中で now() を何回呼んでも同じ日時になるので、「ちょうど今」の境目のテストができる。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
public abstract class MapperTestBase {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 【何のため】ユーザーを作って、番号を返す。メールアドレスは毎回ちがうもの（UUID）にする。
     */
    protected long insertUser(String nickname) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (real_name, nickname, email, password_hash, policies_agreed_at, policies_version)
                VALUES ('テスト 太郎', ?, ?, 'hash', now(), 'test')
                RETURNING id
                """, Long.class, nickname, "mapper-" + UUID.randomUUID() + "@example.com");
    }

    /**
     * 【何のため】コミュニティを作り、オーナーをメンバーに入れて、番号を返す。
     * createdDaysAgo は、何日前に作ったことにするか（並び順のテストのため）。
     */
    protected long insertCommunity(long ownerId, String name, boolean archived, int createdDaysAgo) {
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO communities (name, owner_id, archived_at, created_at)
                VALUES (?, ?, CASE WHEN ?::boolean THEN now() END, now() - make_interval(days => ?))
                RETURNING id
                """, Long.class, name, ownerId, archived, createdDaysAgo);
        insertMember(id, ownerId, "owner");
        return id;
    }

    /** 【何のため】アーカイブしていないコミュニティを作る（名前と作成日時は気にしないとき）。 */
    protected long insertCommunity(long ownerId) {
        return insertCommunity(ownerId, "テストのコミュニティ", false, 0);
    }

    /**
     * 【何のため】メンバーを入れる。role は owner、staff、member のどれか。joinedMinutesAgo は、何分前に参加したか。
     */
    protected void insertMember(long communityId, long userId, String role, int joinedMinutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO memberships (community_id, user_id, role, joined_at)
                VALUES (?, ?, ?, now() - make_interval(mins => ?))
                """, communityId, userId, role, joinedMinutesAgo);
    }

    protected void insertMember(long communityId, long userId, String role) {
        insertMember(communityId, userId, role, 0);
    }

    /**
     * 【何のため】イベントを作って、番号を返す。日時は「今から何分後か」で指定する（マイナスなら過去）。
     * createdMinutesAgo は、何分前に作ったことにするか（検索の並び順のテストのため）。
     */
    protected long insertEvent(long communityId, String title, String description, int capacity,
            int deadlineMinutes, int startMinutes, int endMinutes, int createdMinutesAgo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO events (community_id, title, description, start_at, end_at, application_deadline,
                                    location, capacity, created_at)
                VALUES (?, ?, ?, now() + make_interval(mins => ?), now() + make_interval(mins => ?),
                        now() + make_interval(mins => ?), 'オンライン', ?, now() - make_interval(mins => ?))
                RETURNING id
                """, Long.class, communityId, title, description, startMinutes, endMinutes, deadlineMinutes,
                capacity, createdMinutesAgo);
    }

    /** 【何のため】締め切り前・開催前のイベント（締め切りは1日後、開始は2日後）を作る。 */
    protected long insertUpcomingEvent(long communityId, int capacity) {
        return insertEvent(communityId, "テストのイベント", "", capacity, 60 * 24, 60 * 48, 60 * 50, 0);
    }

    /** 【何のため】締め切りを過ぎた、開催前のイベント（締め切りは1時間前、開始は1日後）を作る。 */
    protected long insertDeadlinePassedEvent(long communityId, int capacity) {
        return insertEvent(communityId, "テストのイベント", "", capacity, -60, 60 * 24, 60 * 26, 0);
    }

    /** 【何のため】終了したイベント（開始は2時間前、終了は1時間前）を作る。 */
    protected long insertEndedEvent(long communityId) {
        return insertEvent(communityId, "終了したイベント", "", 10, -180, -120, -60, 0);
    }

    /**
     * 【何のため】申込みを作って、番号を返す。
     * appliedMinutesAgo は何分前に申し込んだか、queuedMinutesAgo は何分前に席の順番を取ったか（null なら取っていない）。
     * 申込み済み・キャンセル待ちは席の順番が必要、承認待ち・却下は席の順番を取らない（テーブルの制約）。
     */
    protected long insertApplication(long eventId, long userId, String status, int appliedMinutesAgo,
            Integer queuedMinutesAgo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO applications (event_id, user_id, status, applied_at, queued_at)
                VALUES (?, ?, ?, now() - make_interval(mins => ?),
                        CASE WHEN ?::int IS NULL THEN NULL ELSE now() - make_interval(mins => ?::int) END)
                RETURNING id
                """, Long.class, eventId, userId, status, appliedMinutesAgo, queuedMinutesAgo, queuedMinutesAgo);
    }

    /**
     * 【何のため】申込みを作る（自動の申込みと同じく、席の順番＝申し込んだ日時。承認待ち・却下は順番なし）。
     */
    protected long insertApplication(long eventId, long userId, String status, int minutesAgo) {
        boolean queued = "applied".equals(status) || "waitlisted".equals(status);
        return insertApplication(eventId, userId, status, minutesAgo, queued ? minutesAgo : null);
    }

    /** 【何のため】申込みの状態を返す。申込みがなければ null。 */
    protected String applicationStatus(long eventId, long userId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT status FROM applications WHERE event_id = ? AND user_id = ?", String.class, eventId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 【何のため】ほかのテストのデータと混ざらない、検索に使う単語を作る。 */
    protected static String uniqueWord() {
        return "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
