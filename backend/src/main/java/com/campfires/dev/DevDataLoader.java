package com.campfires.dev;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 【役割】開発用の最初のデータ（ユーザー、コミュニティ、イベント、申込み、お知らせ）を、データベースに入れるクラス。
 * 【なぜ必要か】画面を本物の API につないだときも、偽の API（frontend/src/mocks/db）と同じデータで動きを確かめるため。
 * データの作り方は、偽の API と同じルールにしている（番号、名前、日時の計算）。
 *
 * @Profile("dev") を付けたので、dev のプロファイルで起動したとき（./mvnw spring-boot:run）だけ動く。
 * 自動テストと、本番のアプリでは動かない。
 * ユーザーの表が空のときだけデータを入れる。最初の状態に戻したいときは、データベースを作り直す（README を参照）。
 *
 * パスワード（campfire2026）は、開発用の偽の値。起動のたびに BCrypt で変換するので、変換した値はファイルに残さない。
 * ApplicationRunner は「アプリの起動が終わったときに、1回だけ動く」部品。
 */
@Component
@Profile("dev")
public class DevDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

    // 開発用のアカウント全員に共通のパスワード（偽の API の MOCK_PASSWORD と同じ）
    private static final String DEV_PASSWORD = "campfire2026";
    private static final String POLICIES_VERSION = "2026-10-11";

    // 偽の API と同じ数
    private static final int DUMMY_USER_COUNT = 40;
    private static final long FIRST_DUMMY_USER_ID = 5;
    private static final long[] APPLICANT_USER_IDS = { FIRST_DUMMY_USER_ID + DUMMY_USER_COUNT,
            FIRST_DUMMY_USER_ID + DUMMY_USER_COUNT + 1 };
    private static final int EVENT_COUNT = 70;
    private static final int OLD_READ_NOTIFICATION_COUNT = 45;

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    // イベントのタイトル、説明、場所の材料（偽の API の events.ts と同じ）
    private static final String[] TOPICS = { "Java", "Spring Boot", "React", "TypeScript", "SQL", "AWS", "Docker", "Git",
            "アルゴリズム", "テスト" };
    private static final String[] FORMATS = { "初心者向け勉強会", "もくもく会", "ハンズオン", "LT会", "読書会", "質問会", "ペアプロ会" };
    private static final String[] DESCRIPTIONS = {
            "週末の午後に、みんなで手を動かしながら学びます。初心者の方も歓迎です。",
            "平日の夜にオンラインで開催します。質問はチャットでどうぞ。",
            "会場で集まって開催します。ノートパソコンを持ってきてください。",
            "実務で使っている人の話を聞いて、疑問を解消する会です。",
            "本を1章ずつ読み進めます。読んでいなくても参加できます。" };
    private static final String[] LOCATIONS = { "オンライン（参加者に URL をお知らせします）", "渋谷区の貸し会議室", "新宿区のコワーキングスペース" };

    // コミュニティの名前、説明、オーナー（偽の API の communities.ts と同じ）
    private record CommunitySeed(String name, String description, long ownerId) {
    }

    private static final List<CommunitySeed> COMMUNITIES = List.of(
            new CommunitySeed("Java 勉強会 東京", "Java を学ぶ人のための勉強会です。初心者も歓迎します。", 1),
            new CommunitySeed("フロントエンドもくもく会", "React や TypeScript を、それぞれのペースで学ぶ会です。", 1),
            new CommunitySeed("クラウド入門の会", "AWS や Docker の基本を、手を動かして学びます。", FIRST_DUMMY_USER_ID),
            new CommunitySeed("データベース読書会", "SQL とデータベース設計の本を、みんなで読み進めます。", FIRST_DUMMY_USER_ID + 1),
            new CommunitySeed("はじめてのプログラミング", "プログラミングを始めたばかりの人が集まる会です。", FIRST_DUMMY_USER_ID + 2));

    // イベントの番号と、作るときに計算した値（申込みとお知らせを作るときに使う）
    private record EventSeed(long id, long communityId, String title, int capacity, Instant createdAt) {
    }

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DevDataLoader(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 【何のため】ユーザーの表が空なら、最初のデータを入れる。途中で失敗したら、すべて取り消す（@Transactional）。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        if (users != null && users > 0) {
            log.info("開発用のデータは、すでに入っています（入れ直すときは、README の手順でデータベースを作り直してください）");
            return;
        }
        Instant now = Instant.now();
        insertUsers(now);
        insertCommunities(now);
        insertMemberships(now);
        insertJoinRequests(now);
        List<EventSeed> events = insertEvents(now);
        insertApplications(events);
        insertNotifications(events, now);
        log.info("開発用のデータを入れました（パスワードは、すべて {}）", DEV_PASSWORD);
    }

    // ---------- ユーザー（偽の API の users.ts） ----------

    private void insertUsers(Instant now) {
        // BCrypt の変換は時間がかかるので、1回だけ変換して、全員に同じ値を使う
        String hash = passwordEncoder.encode(DEV_PASSWORD);
        Instant demoCreatedAt = now.minus(DAY.multipliedBy(100));
        insertUser(1, "山田 太郎", "たろう", "owner@example.com", hash, demoCreatedAt);
        insertUser(2, "佐藤 花子", "はなこ", "staff@example.com", hash, demoCreatedAt);
        insertUser(3, "鈴木 次郎", "じろう", "member@example.com", hash, demoCreatedAt);
        insertUser(4, "高橋 さくら", "さくら", "guest@example.com", hash, demoCreatedAt);
        // 参加者01〜参加者40
        for (int i = 0; i < DUMMY_USER_COUNT; i++) {
            String number = String.format("%02d", i + 1);
            insertUser(FIRST_DUMMY_USER_ID + i, "参加者 " + number, "参加者" + number, "user" + number + "@example.com",
                    hash, now.minus(DAY.multipliedBy(90 - i)));
        }
        // 参加申請を試すための、どのコミュニティにも入っていないユーザー
        String[] nicknames = { "みさき", "けんた" };
        for (int i = 0; i < nicknames.length; i++) {
            insertUser(APPLICANT_USER_IDS[i], "申請者 " + (i + 1), nicknames[i], "applicant" + (i + 1) + "@example.com",
                    hash, now.minus(DAY.multipliedBy(10)));
        }
        // 番号を指定して入れたので、次に自動で付ける番号を、いちばん大きい番号の次にそろえる
        resetIdentity("users");
    }

    // OVERRIDING SYSTEM VALUE：自動で番号を付ける列（IDENTITY）に、番号を指定して入れるための書き方
    private void insertUser(long id, String realName, String nickname, String email, String hash, Instant createdAt) {
        jdbc.update("""
                INSERT INTO users (id, real_name, nickname, email, password_hash, policies_agreed_at, policies_version, created_at)
                OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, realName, nickname, email, hash, ts(createdAt), POLICIES_VERSION, ts(createdAt));
    }

    // ---------- コミュニティとメンバー（偽の API の communities.ts） ----------

    private void insertCommunities(Instant now) {
        for (int i = 0; i < COMMUNITIES.size(); i++) {
            CommunitySeed seed = COMMUNITIES.get(i);
            // 番号が大きいほど新しく作られたことにする
            jdbc.update("""
                    INSERT INTO communities (id, name, description, owner_id, created_at)
                    OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?)
                    """, i + 1, seed.name(), seed.description(), seed.ownerId(), ts(now.minus(DAY.multipliedBy(60 - i * 5))));
        }
        resetIdentity("communities");
    }

    private void insertMemberships(Instant now) {
        Timestamp joinedAt = ts(now.minus(DAY.multipliedBy(50)));
        String sql = "INSERT INTO memberships (community_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)";
        for (int i = 0; i < COMMUNITIES.size(); i++) {
            long communityId = i + 1;
            long ownerId = COMMUNITIES.get(i).ownerId();
            jdbc.update(sql, communityId, ownerId, "owner", joinedAt);
            // 参加者01〜40 は、すべてのコミュニティのメンバー（オーナーは除く）
            for (int n = 0; n < DUMMY_USER_COUNT; n++) {
                long userId = FIRST_DUMMY_USER_ID + n;
                if (userId != ownerId) {
                    jdbc.update(sql, communityId, userId, "member", joinedAt);
                }
            }
        }
        // Java 勉強会（1）の運営スタッフ（はなこ）とメンバー（じろう）。さくら（4）は、どこにも入っていない
        jdbc.update(sql, 1, 2, "staff", joinedAt);
        jdbc.update(sql, 1, 3, "member", joinedAt);
    }

    private void insertJoinRequests(Instant now) {
        // みさきとけんたが、Java 勉強会（1）に申請中
        for (int i = 0; i < APPLICANT_USER_IDS.length; i++) {
            jdbc.update("INSERT INTO join_requests (community_id, user_id, status, requested_at) VALUES (1, ?, 'pending', ?)",
                    APPLICANT_USER_IDS[i], ts(now.minus(DAY.multipliedBy(3 - i))));
        }
    }

    // ---------- イベントと申込み（偽の API の events.ts） ----------

    private List<EventSeed> insertEvents(Instant now) {
        List<EventSeed> events = new ArrayList<>();
        for (int i = 0; i < EVENT_COUNT; i++) {
            long id = i + 1;
            long communityId = (i % COMMUNITIES.size()) + 1;
            String title = TOPICS[i % TOPICS.length] + " " + FORMATS[i % FORMATS.length];
            // 8件に1件は過去（終了済み）、それ以外は1〜60日後
            boolean isPast = i % 8 == 0;
            Instant startAt = isPast ? now.minus(DAY.multipliedBy(i + 1)) : now.plus(DAY.multipliedBy((i % 60) + 1));
            int capacity = 10 + (i % 4) * 10;
            Instant createdAt = now.minus(HOUR.multipliedBy(EVENT_COUNT - i));
            // 参加の承認の方法（Issue #43）：20件に1件はメンバーもメンバー以外も手動、別の20件に1件はメンバー以外も自動。
            // それ以外は最初の値（メンバーは自動、メンバー以外は受け付けない）
            String memberApproval = i % 20 == 10 ? "manual" : "auto";
            String nonMemberApproval = i % 20 == 10 ? "manual" : i % 20 == 15 ? "auto" : "closed";
            jdbc.update("""
                    INSERT INTO events (id, community_id, title, description, start_at, end_at, application_deadline,
                                        location, capacity, member_approval, non_member_approval, created_at)
                    OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, communityId, title, DESCRIPTIONS[i % DESCRIPTIONS.length], ts(startAt),
                    ts(startAt.plus(HOUR.multipliedBy(2))), ts(startAt.minus(DAY)), LOCATIONS[i % LOCATIONS.length],
                    capacity, memberApproval, nonMemberApproval, ts(createdAt));
            events.add(new EventSeed(id, communityId, title, capacity, createdAt));
        }
        resetIdentity("events");
        return events;
    }

    private void insertApplications(List<EventSeed> events) {
        // 申込み済み・キャンセル待ちは、席の順番（queued_at）も、申し込んだ日時と同じにする
        String sql = "INSERT INTO applications (event_id, user_id, status, applied_at, queued_at) VALUES (?, ?, ?, ?, ?)";
        for (int i = 0; i < events.size(); i++) {
            EventSeed event = events.get(i);
            // 5件に1件は満席、それ以外は定員の範囲で、参加者01から順に申し込む
            int appliedCount = i % 5 == 0 ? event.capacity() : (i * 3) % event.capacity();
            // 申込みは、イベントが作られた10分後（必ず今より前にする。キャンセル待ちの順番がおかしくならないように）
            Instant appliedAt = event.createdAt().plus(Duration.ofMinutes(10));
            for (int n = 0; n < appliedCount; n++) {
                jdbc.update(sql, event.id(), FIRST_DUMMY_USER_ID + n, "applied", ts(appliedAt), ts(appliedAt));
            }
            // 満席で、定員が30人以下のイベントには、キャンセル待ちを2人入れる
            if (appliedCount == event.capacity() && event.capacity() <= 30) {
                for (int n = 0; n < 2; n++) {
                    Instant waitlistedAt = appliedAt.plus(Duration.ofMinutes(n + 1));
                    jdbc.update(sql, event.id(), FIRST_DUMMY_USER_ID + event.capacity() + n, "waitlisted",
                            ts(waitlistedAt), ts(waitlistedAt));
                }
            }
        }
        // 承認待ちの申込み（承認・却下を試すため）：手動のイベント 11 に、じろう（メンバー）とさくら（メンバー以外）
        EventSeed manualEvent = events.get(10);
        Instant pendingAt = manualEvent.createdAt().plus(Duration.ofMinutes(30));
        jdbc.update(sql, manualEvent.id(), 3, "pending", ts(pendingAt), null);
        jdbc.update(sql, manualEvent.id(), 4, "pending", ts(pendingAt.plus(Duration.ofMinutes(1))), null);
    }

    // ---------- お知らせ（偽の API の notifications.ts） ----------

    private void insertNotifications(List<EventSeed> events, Instant now) {
        // Java 勉強会（コミュニティ 1）のイベント
        List<EventSeed> javaEvents = events.stream().filter(e -> e.communityId() == 1).toList();
        String sql = """
                INSERT INTO notifications (user_id, type, event_title, community_id, event_id, actor_user_id, read_at, created_at)
                VALUES (1, 'late_cancellation', ?, ?, ?, ?, ?, ?)
                """;
        // 古い既読のお知らせ 45件（スクロールで続きを読み込む動きを試すため）
        for (int i = 0; i < OLD_READ_NOTIFICATION_COUNT; i++) {
            EventSeed event = javaEvents.get(i % javaEvents.size());
            Instant readAt = now.minus(DAY.multipliedBy(2 + i));
            jdbc.update(sql, event.title(), event.communityId(), event.id(), 7 + (i % 10), ts(readAt),
                    ts(readAt.minus(HOUR)));
        }
        // 新しいお知らせ：既読1件、未読1件
        EventSeed first = javaEvents.get(0);
        EventSeed second = javaEvents.get(1);
        jdbc.update(sql, first.title(), first.communityId(), first.id(), 6, ts(now.minus(HOUR.multipliedBy(20))),
                ts(now.minus(HOUR.multipliedBy(24))));
        jdbc.update(sql, second.title(), second.communityId(), second.id(), 7, null, ts(now.minus(HOUR.multipliedBy(2))));
    }

    // ---------- 共通 ----------

    /**
     * 【何のため】番号を指定して行を入れた表で、次に自動で付ける番号を「いちばん大きい番号の次」にそろえる。
     * そろえないと、画面から新しい行を作ったときに、入れた番号と重なってエラーになる。
     * 表の名前は、このクラスの中で決めた固定の文字だけを渡す（外から受け取った値は使わない）。
     */
    private void resetIdentity(String table) {
        jdbc.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), (SELECT max(id) FROM " + table + "))");
    }

    // 日時を、データベースに渡せる形（Timestamp）に変える
    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
