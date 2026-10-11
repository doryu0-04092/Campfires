package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.campfires.TestcontainersConfiguration;

/**
 * 【役割】たくさんの人が、同時に同じイベントに申し込んだときのテスト（F-14 定員の同時実行制御）。
 * 【なぜ必要か】「残り1枠に、同時に何人も申し込む」と、プログラムによっては、全員が「空いている」と判断して、
 * 定員を超えてしまう。ロックのしくみ（ApplicationService.apply）が正しく働き、
 * 何人が同時に申し込んでも、定員の超過が 0件であることを、本物のデータベースで確かめる。
 *
 * API を通さずに ApplicationService を直接呼ぶ。100個のスレッド（同時に動く処理の流れ）から、一斉に呼ぶ。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConcurrentApplicationTest {

    // 同時に申し込む人数
    private static final int USERS = 100;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 【何のため】100人が同時に申し込んで、何件が申込み済みになったか（とエラーの数）を返す。
     * @param capacity イベントの定員
     */
    private Result applyAtOnce(int capacity) throws Exception {
        // テストのユーザーを、SQL でまとめて100人作る（パスワードは使わないので、ダミーの値）
        String prefix = UUID.randomUUID().toString();
        List<Long> userIds = jdbcTemplate.queryForList("""
                INSERT INTO users (real_name, nickname, email, password_hash, policies_agreed_at, policies_version)
                SELECT '同時', '同時', ? || '-' || n || '@example.com', 'x', now(), 'test'
                FROM generate_series(1, ?) AS n
                RETURNING id
                """, Long.class, prefix, USERS);
        long ownerId = userIds.get(0);
        long communityId = jdbcTemplate.queryForObject(
                "INSERT INTO communities (name, owner_id) VALUES ('同時', ?) RETURNING id", Long.class, ownerId);
        for (long userId : userIds) {
            jdbcTemplate.update("INSERT INTO memberships (community_id, user_id, role) VALUES (?, ?, ?)",
                    communityId, userId, userId == ownerId ? "owner" : "member");
        }
        long eventId = jdbcTemplate.queryForObject("""
                INSERT INTO events (community_id, title, start_at, end_at, application_deadline, location, capacity)
                VALUES (?, '同時', now() + interval '2 days', now() + interval '3 days', now() + interval '1 day', '会場', ?)
                RETURNING id
                """, Long.class, communityId, capacity);

        // CountDownLatch は「合図」の道具。全員が準備できてから、一斉に申し込ませる
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(USERS);
        List<Future<?>> futures = new ArrayList<>();
        for (long userId : userIds) {
            futures.add(executor.submit(() -> {
                start.await();
                return applicationService.apply(userId, communityId, eventId);
            }));
        }
        start.countDown();
        int errors = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                errors++;
            }
        }
        executor.shutdown();

        int applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM applications WHERE event_id = ? AND status = 'applied'", Integer.class, eventId);
        int waitlisted = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM applications WHERE event_id = ? AND status = 'waitlisted'", Integer.class, eventId);
        // キャンセル待ちの順番が、1, 2, 3… と重ならずに並ぶか（申し込んだ日時が、すべて違うか）
        int distinctTimes = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT applied_at) FROM applications WHERE event_id = ?", Integer.class, eventId);
        return new Result(applied, waitlisted, errors, distinctTimes);
    }

    private record Result(int applied, int waitlisted, int errors, int distinctTimes) {
    }

    /**
     * 【何のため】残り1枠（定員1）に100人が同時に申し込んでも、申込み済みは1人だけで、残りはキャンセル待ちになる。
     */
    @Test
    void 残り1枠に100人が同時に申し込んでも定員を超えない() throws Exception {
        Result result = applyAtOnce(1);
        assertThat(result.errors()).isZero();
        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.waitlisted()).isEqualTo(USERS - 1);
        assertThat(result.distinctTimes()).isEqualTo(USERS);
    }

    /**
     * 【何のため】定員10に100人が同時に申し込んでも、申込み済みはちょうど10人になる。
     */
    @Test
    void 定員10に100人が同時に申し込むとちょうど10人が申込み済みになる() throws Exception {
        Result result = applyAtOnce(10);
        assertThat(result.errors()).isZero();
        assertThat(result.applied()).isEqualTo(10);
        assertThat(result.waitlisted()).isEqualTo(USERS - 10);
    }

    /**
     * 【何のため】承認の方法が手動のイベントで、承認待ちの99人を同時に承認しても、定員5人を超えないことを確かめる（Issue #43）。
     * 承認も、申込みと同じく、イベントの行をロックしてから人数を数えるので、超えないはず。
     */
    @Test
    void 承認待ちを同時に承認しても定員を超えない() throws Exception {
        String prefix = UUID.randomUUID().toString();
        List<Long> userIds = jdbcTemplate.queryForList("""
                INSERT INTO users (real_name, nickname, email, password_hash, policies_agreed_at, policies_version)
                SELECT '同時', '同時', ? || '-' || n || '@example.com', 'x', now(), 'test'
                FROM generate_series(1, ?) AS n
                RETURNING id
                """, Long.class, prefix, USERS);
        long ownerId = userIds.get(0);
        List<Long> applicants = userIds.subList(1, userIds.size());
        long communityId = jdbcTemplate.queryForObject(
                "INSERT INTO communities (name, owner_id) VALUES ('同時', ?) RETURNING id", Long.class, ownerId);
        jdbcTemplate.update("INSERT INTO memberships (community_id, user_id, role) VALUES (?, ?, 'owner')",
                communityId, ownerId);
        long eventId = jdbcTemplate.queryForObject("""
                INSERT INTO events (community_id, title, start_at, end_at, application_deadline, location, capacity,
                                    member_approval, non_member_approval)
                VALUES (?, '同時', now() + interval '2 days', now() + interval '3 days', now() + interval '1 day', '会場', 5,
                        'manual', 'manual')
                RETURNING id
                """, Long.class, communityId);
        for (long userId : applicants) {
            jdbcTemplate.update("INSERT INTO applications (event_id, user_id, status) VALUES (?, ?, 'pending')",
                    eventId, userId);
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(applicants.size());
        List<Future<?>> futures = new ArrayList<>();
        for (long userId : applicants) {
            futures.add(executor.submit(() -> {
                start.await();
                return applicationService.decide(ownerId, communityId, eventId, userId, "approved");
            }));
        }
        start.countDown();
        int errors = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                errors++;
            }
        }
        executor.shutdown();

        assertThat(errors).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM applications WHERE event_id = ? AND status = 'applied'", Integer.class, eventId))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM applications WHERE event_id = ? AND status = 'waitlisted'", Integer.class, eventId))
                .isEqualTo(applicants.size() - 5);
    }
}
