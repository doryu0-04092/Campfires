package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.campfires.ApiTestBase;

/**
 * 【役割】イベントの API（一覧・検索、作成、詳細、編集、削除）のテスト。
 * 【なぜ必要か】API 仕様書（docs/api/openapi.yaml の events）と、要件定義書 5.2（イベント）、
 * 5.8（一覧と検索）、5.9（アーカイブ）、5.10（お知らせ）のルールのとおりに動くことを確かめるため。
 */
class EventApiTest extends ApiTestBase {

    /** 【何のため】今から何時間後かの日時を、API に送る形の文字にする。 */
    private static String hoursLater(int hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** 【何のため】正しいイベントの入力を作る。HashMap にして、テストごとに一部を書き換えられるようにする。 */
    private static Map<String, Object> validInput(int capacity) {
        Map<String, Object> input = new HashMap<>();
        input.put("title", "Java 勉強会");
        input.put("description", "初心者向け");
        input.put("startAt", hoursLater(48));
        input.put("endAt", hoursLater(50));
        input.put("applicationDeadline", hoursLater(24));
        input.put("location", "オンライン");
        input.put("capacity", capacity);
        return input;
    }

    private static String eventUrl(long communityId, long eventId) {
        return "/api/communities/" + communityId + "/events/" + eventId;
    }

    // ---------- 作成 ----------

    /**
     * 【何のため】オーナーと運営スタッフはイベントを作れ、メンバーは作れないことを確かめる。
     */
    @Test
    void イベントを作れるのはオーナーと運営スタッフ() throws Exception {
        Account owner = newAccount();
        Account staff = newAccount();
        Account member = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, staff.id(), "staff");
        insertMember(communityId, member.id(), "member");
        String url = "/api/communities/" + communityId + "/events";

        mockMvc.perform(as(owner, post(url), validInput(20)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.startsWith(url + "/")))
                .andExpect(jsonPath("$.title").value("Java 勉強会"))
                .andExpect(jsonPath("$.capacity").value(20))
                .andExpect(jsonPath("$.remainingSeats").value(20))
                .andExpect(jsonPath("$.waitlistCount").value(0))
                .andExpect(jsonPath("$.myRole").value("owner"))
                .andExpect(jsonPath("$.myApplication").isEmpty())
                .andExpect(jsonPath("$.communityArchivedAt").isEmpty());
        mockMvc.perform(as(staff, post(url), validInput(20))).andExpect(status().isCreated());
        mockMvc.perform(as(member, post(url), validInput(20)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * 【何のため】日時の前後関係を含めて、正しくない入力は、項目ごとのエラーがまとめて返ることを確かめる。
     */
    @Test
    void 入力が正しくなければ項目ごとにエラーを返す() throws Exception {
        Account owner = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        Map<String, Object> input = validInput(0);
        input.put("title", " ");
        input.put("startAt", hoursLater(10));
        input.put("endAt", hoursLater(5));
        input.put("applicationDeadline", hoursLater(20));

        mockMvc.perform(as(owner, post("/api/communities/" + communityId + "/events"), input))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].message").value("タイトルを入力してください。"))
                .andExpect(jsonPath("$.errors[?(@.field == 'capacity')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'endAt')].message").value("終了日時は、開始日時より後にしてください。"))
                .andExpect(jsonPath("$.errors[?(@.field == 'applicationDeadline')].message")
                        .value("申込み締め切り日時は、開始日時より前にしてください。"));

        // 開始日時が過去、日時の形が正しくない
        Map<String, Object> past = validInput(10);
        past.put("startAt", hoursLater(-1));
        past.put("endAt", "あした");
        mockMvc.perform(as(owner, post("/api/communities/" + communityId + "/events"), past))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'startAt')].message").value("開始日時は、今より後にしてください。"))
                .andExpect(jsonPath("$.errors[?(@.field == 'endAt')].message").value("終了日時を入力してください。"));
    }

    /**
     * 【何のため】アーカイブ中のコミュニティには作れないこと（要件定義書 5.9）を確かめる。
     */
    @Test
    void アーカイブ中のコミュニティにはイベントを作れない() throws Exception {
        Account owner = newAccount();
        long communityId = insertCommunity(owner.id(), true);
        mockMvc.perform(as(owner, post("/api/communities/" + communityId + "/events"), validInput(10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_ARCHIVED"));
    }

    // ---------- 一覧、検索、詳細 ----------

    /**
     * 【何のため】一覧・検索のルール（要件定義書 5.8）を確かめる。
     * 終了したイベントは出さず、アーカイブ中のコミュニティのイベントは出す。当てはまった単語が多い順。
     */
    @Test
    void 検索は開催前のイベントを当てはまった単語の数が多い順に返す() throws Exception {
        Account owner = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long archivedCommunity = insertCommunity(owner.id(), true);
        String w1 = uniqueWord();
        String w2 = uniqueWord();
        long one = insertEvent(communityId, w1, 10, 24, 48, 50);
        long both = insertEvent(archivedCommunity, w1 + " " + w2.toUpperCase(), 10, 24, 48, 50);
        insertEvent(communityId, w1 + " " + w2 + " 終了済み", 10, -50, -48, -46);

        mockMvc.perform(get("/api/events").param("q", w1 + "　" + w2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value(both))
                .andExpect(jsonPath("$.items[0].communityName").value("テストのコミュニティ"))
                .andExpect(jsonPath("$.items[1].id").value(one))
                .andExpect(jsonPath("$.limitExceeded").value(false));
    }

    /**
     * 【何のため】コミュニティのイベントの一覧で、開催前と終了済みを分けて、決まった順に返すことを確かめる。
     */
    @Test
    void コミュニティのイベントは開催前と終了済みに分けて返す() throws Exception {
        Account owner = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long later = insertEvent(communityId, "後", 10, 24, 72, 74);
        long sooner = insertEvent(communityId, "先", 10, 24, 48, 50);
        long oldPast = insertEvent(communityId, "昔", 10, -100, -96, -94);
        long recentPast = insertEvent(communityId, "最近", 10, -50, -48, -46);
        String url = "/api/communities/" + communityId + "/events";

        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value(sooner))
                .andExpect(jsonPath("$.items[1].id").value(later));
        mockMvc.perform(get(url).param("status", "past"))
                .andExpect(jsonPath("$.items[0].id").value(recentPast))
                .andExpect(jsonPath("$.items[1].id").value(oldPast));
        mockMvc.perform(get("/api/communities/999999999/events")).andExpect(status().isNotFound());
    }

    /**
     * 【何のため】詳細はログインしていなくても見られ、ちがうコミュニティの番号では 404 になることを確かめる。
     */
    @Test
    void 詳細はログインしていなくても見られる() throws Exception {
        Account owner = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long otherCommunity = insertCommunity(owner.id(), false);
        long eventId = insertEvent(communityId, "詳細", 10, 24, 48, 50);

        mockMvc.perform(get(eventUrl(communityId, eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("オンライン"))
                .andExpect(jsonPath("$.myRole").isEmpty())
                .andExpect(jsonPath("$.myApplication").isEmpty());
        mockMvc.perform(get(eventUrl(otherCommunity, eventId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("イベントが見つかりません。"));
    }

    // ---------- 編集 ----------

    /**
     * 【何のため】編集のルール（要件定義書 5.2）を確かめる。
     * - 定員を、申込み済みの人数より少なくできない
     * - 定員を増やすと、キャンセル待ちの人が繰り上がる
     */
    @Test
    void 定員を増やすとキャンセル待ちが繰り上がる() throws Exception {
        Account owner = newAccount();
        Account a = newAccount();
        Account b = newAccount();
        Account c = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long eventId = insertEvent(communityId, "編集", 1, 24, 48, 50);
        insertApplication(eventId, a.id(), "applied", 30);
        insertApplication(eventId, b.id(), "waitlisted", 20);
        insertApplication(eventId, c.id(), "waitlisted", 10);

        mockMvc.perform(as(owner, patch(eventUrl(communityId, eventId)), validInput(0)))
                .andExpect(status().isBadRequest());
        // 定員 2 にすると、キャンセル待ちの先頭（b）だけが繰り上がる
        mockMvc.perform(as(owner, patch(eventUrl(communityId, eventId)), validInput(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.remainingSeats").value(0))
                .andExpect(jsonPath("$.waitlistCount").value(1));
        assertThat(applicationStatus(eventId, b.id())).isEqualTo("applied");
        assertThat(applicationStatus(eventId, c.id())).isEqualTo("waitlisted");

        // 申込み済みは2人なので、定員を1にはできない
        mockMvc.perform(as(owner, patch(eventUrl(communityId, eventId)), validInput(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_APPLIED"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("2人")));
    }

    /**
     * 【何のため】終了したイベントは編集・削除できず、メンバーは編集できないことを確かめる。
     */
    @Test
    void 終了したイベントは編集も削除もできない() throws Exception {
        Account owner = newAccount();
        Account member = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, member.id(), "member");
        long ended = insertEvent(communityId, "終了", 10, -50, -48, -46);
        long upcoming = insertEvent(communityId, "開催前", 10, 24, 48, 50);

        mockMvc.perform(as(owner, patch(eventUrl(communityId, ended)), validInput(10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_ENDED"));
        mockMvc.perform(as(owner, delete(eventUrl(communityId, ended))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_ENDED"));
        mockMvc.perform(as(member, patch(eventUrl(communityId, upcoming)), validInput(10)))
                .andExpect(status().isForbidden());
    }

    // ---------- 削除 ----------

    /**
     * 【何のため】削除すると、申込みも消え、申し込んでいた人（操作した人を除く）にお知らせが届くことを確かめる。
     */
    @Test
    void 削除すると申し込んでいた人にお知らせが届く() throws Exception {
        Account owner = newAccount();
        Account staff = newAccount();
        Account applied = newAccount();
        Account waiting = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, staff.id(), "staff");
        long eventId = insertEvent(communityId, "削除されるイベント", 1, 24, 48, 50);
        insertApplication(eventId, staff.id(), "applied", 30);
        insertApplication(eventId, applied.id(), "waitlisted", 20);
        insertApplication(eventId, waiting.id(), "waitlisted", 10);

        // 運営スタッフ（自分も申し込んでいる）が削除する
        mockMvc.perform(as(staff, delete(eventUrl(communityId, eventId)))).andExpect(status().isNoContent());

        mockMvc.perform(get(eventUrl(communityId, eventId))).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM applications WHERE event_id = ?", Integer.class,
                eventId)).isZero();
        List<Long> notified = jdbcTemplate.queryForList("""
                SELECT user_id FROM notifications
                WHERE type = 'event_deleted' AND event_title = '削除されるイベント' AND community_id = ?
                """, Long.class, communityId);
        assertThat(notified).containsExactlyInAnyOrder(applied.id(), waiting.id());
    }
}
