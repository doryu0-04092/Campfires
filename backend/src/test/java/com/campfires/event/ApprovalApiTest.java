package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.campfires.ApiTestBase;

/**
 * 【役割】イベントの参加の承認（自動・手動）のテスト（Issue #43、要件定義書 5.12）。
 * 【なぜ必要か】承認の方法ごとの申込みの動き、承認・却下、席の確保のタイミング、方法を変えたときの動きが、
 * 決めたルールのとおりになっていることを確かめるため。
 */
class ApprovalApiTest extends ApiTestBase {

    private static String hoursLater(int hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** 【何のため】イベントの入力を作る。承認の方法を指定する（null なら送らない）。 */
    private static Map<String, Object> input(int capacity, String memberApproval, String nonMemberApproval) {
        Map<String, Object> input = new HashMap<>();
        input.put("title", "承認のテスト");
        input.put("description", "");
        input.put("startAt", hoursLater(48));
        input.put("endAt", hoursLater(50));
        input.put("applicationDeadline", hoursLater(24));
        input.put("location", "オンライン");
        input.put("capacity", capacity);
        if (memberApproval != null) {
            input.put("memberApproval", memberApproval);
        }
        if (nonMemberApproval != null) {
            input.put("nonMemberApproval", nonMemberApproval);
        }
        return input;
    }

    /** 【何のため】承認の方法を指定して、イベントを作る（SQL で直接）。 */
    private long insertEventWithApproval(long communityId, int capacity, String memberApproval,
            String nonMemberApproval) {
        long eventId = insertEvent(communityId, "承認のテスト", capacity, 24, 48, 50);
        jdbcTemplate.update("UPDATE events SET member_approval = ?, non_member_approval = ? WHERE id = ?",
                memberApproval, nonMemberApproval, eventId);
        return eventId;
    }

    private static String eventUrl(long communityId, long eventId) {
        return "/api/communities/" + communityId + "/events/" + eventId;
    }

    /** 【何のため】オーナーか運営スタッフとして、申込みを承認・却下する。 */
    private org.springframework.test.web.servlet.ResultActions decide(Account manager, long communityId, long eventId,
            long userId, String status) throws Exception {
        return mockMvc.perform(as(manager, patch(eventUrl(communityId, eventId) + "/applications/" + userId),
                Map.of("status", status)));
    }

    /** 【何のため】お知らせの種類の一覧を返す。 */
    private List<String> notificationTypes(long userId, long eventId) {
        return jdbcTemplate.queryForList("SELECT type FROM notifications WHERE user_id = ? AND event_id = ? ORDER BY id",
                String.class, userId, eventId);
    }

    // ---------- 承認の方法の設定 ----------

    /**
     * 【何のため】作成のときに承認の方法を選べること、選ばなければ最初の値（メンバーは自動、メンバー以外は受け付けない）になることを確かめる。
     */
    @Test
    void 作成のときに承認の方法を選べる() throws Exception {
        Account owner = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        String url = "/api/communities/" + communityId + "/events";

        mockMvc.perform(as(owner, post(url), input(10, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberApproval").value("auto"))
                .andExpect(jsonPath("$.nonMemberApproval").value("closed"))
                .andExpect(jsonPath("$.pendingCount").value(0));
        mockMvc.perform(as(owner, post(url), input(10, "manual", "auto")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberApproval").value("manual"))
                .andExpect(jsonPath("$.nonMemberApproval").value("auto"));
        mockMvc.perform(as(owner, post(url), input(10, "closed", "everyone")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'memberApproval')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'nonMemberApproval')]").exists());
    }

    // ---------- メンバー以外の申込み ----------

    /**
     * 【何のため】メンバー以外は、受け付けないなら 403、自動ならすぐに申込み済み、手動なら承認待ちになることを確かめる。
     */
    @Test
    void メンバー以外の申込みは承認の方法で変わる() throws Exception {
        Account owner = newAccount();
        Account outsider = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long closed = insertEventWithApproval(communityId, 10, "auto", "closed");
        long auto = insertEventWithApproval(communityId, 10, "auto", "auto");
        long manual = insertEventWithApproval(communityId, 10, "auto", "manual");

        mockMvc.perform(as(outsider, post(eventUrl(communityId, closed) + "/applications")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));
        mockMvc.perform(as(outsider, post(eventUrl(communityId, auto) + "/applications")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("applied"));
        mockMvc.perform(as(outsider, post(eventUrl(communityId, manual) + "/applications")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.waitlistPosition").isEmpty());
        // 詳細では、メンバーでないので myRole は null。申込みの状況は返る
        mockMvc.perform(get(eventUrl(communityId, manual)).session(outsider.session()))
                .andExpect(jsonPath("$.myRole").isEmpty())
                .andExpect(jsonPath("$.myApplication.status").value("pending"));
    }

    // ---------- 手動の承認と却下 ----------

    /**
     * 【何のため】手動のときの流れを確かめる。
     * - 承認待ちは定員に数えない。申込者一覧の先頭に出る。自分が申し込んだイベントにも出る
     * - 承認すると申込み済みになり、お知らせが届く。もう一度処理すると 409
     * - 却下すると、お知らせが届き、申し込み直せない。却下された申込みは取り消せない
     */
    @Test
    void 手動のときはオーナーか運営スタッフが承認と却下をする() throws Exception {
        Account owner = newAccount();
        Account staff = newAccount();
        Account alice = newAccount();
        Account bob = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, staff.id(), "staff");
        insertMember(communityId, alice.id(), "member");
        insertMember(communityId, bob.id(), "member");
        long eventId = insertEventWithApproval(communityId, 10, "manual", "closed");
        insertApplication(eventId, owner.id(), "applied", 60);

        mockMvc.perform(as(alice, post(eventUrl(communityId, eventId) + "/applications")))
                .andExpect(jsonPath("$.status").value("pending"));
        mockMvc.perform(as(bob, post(eventUrl(communityId, eventId) + "/applications")))
                .andExpect(jsonPath("$.status").value("pending"));

        // 承認待ちは、定員に数えない
        mockMvc.perform(get(eventUrl(communityId, eventId)))
                .andExpect(jsonPath("$.remainingSeats").value(9))
                .andExpect(jsonPath("$.pendingCount").value(2));
        // 申込者一覧：承認待ち（申し込んだ順）→ 申込み済み
        mockMvc.perform(get(eventUrl(communityId, eventId) + "/applications").session(staff.session()))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[0].user.userId").value(alice.id()))
                .andExpect(jsonPath("$.items[0].status").value("pending"))
                .andExpect(jsonPath("$.items[1].user.userId").value(bob.id()))
                .andExpect(jsonPath("$.items[2].status").value("applied"));
        mockMvc.perform(get("/api/me/events").session(alice.session()))
                .andExpect(jsonPath("$.items[?(@.id == " + eventId + ")].myApplication.status").value("pending"));

        // メンバーは承認できない
        decide(bob, communityId, eventId, alice.id(), "approved").andExpect(status().isForbidden());
        // 運営スタッフが承認する
        decide(staff, communityId, eventId, alice.id(), "approved")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("applied"))
                .andExpect(jsonPath("$.user.userId").value(alice.id()));
        assertThat(notificationTypes(alice.id(), eventId)).containsExactly("application_approved");
        decide(staff, communityId, eventId, alice.id(), "rejected")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_DECIDED"));

        // オーナーが却下する
        decide(owner, communityId, eventId, bob.id(), "rejected")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"));
        assertThat(notificationTypes(bob.id(), eventId)).containsExactly("application_rejected");
        mockMvc.perform(as(bob, post(eventUrl(communityId, eventId) + "/applications")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_REJECTED"));
        mockMvc.perform(as(bob, delete(eventUrl(communityId, eventId) + "/my-application")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_APPLIED"));
        mockMvc.perform(get(eventUrl(communityId, eventId)).session(bob.session()))
                .andExpect(jsonPath("$.myApplication.status").value("rejected"));
        // 却下した人は、申込者一覧に出ない
        mockMvc.perform(get(eventUrl(communityId, eventId) + "/applications").session(owner.session()))
                .andExpect(jsonPath("$.totalCount").value(2));

        // 申込みがない人、正しくない状態は、エラー
        decide(owner, communityId, eventId, staff.id(), "approved").andExpect(status().isNotFound());
        decide(owner, communityId, eventId, alice.id(), "pending").andExpect(status().isBadRequest());
    }

    /**
     * 【何のため】満席のときに承認すると、キャンセル待ちになり、承認された順に並ぶことを確かめる。
     * 先に申し込んでいても、承認待ちの間は順番を取らない。
     */
    @Test
    void 満席のときに承認するとキャンセル待ちの後ろに並ぶ() throws Exception {
        Account owner = newAccount();
        Account seated = newAccount();
        Account earlyPending = newAccount();
        Account waiting = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long eventId = insertEventWithApproval(communityId, 1, "manual", "manual");
        insertApplication(eventId, seated.id(), "applied", 50);
        // earlyPending は、waiting より先に申し込んだが、承認待ちのまま
        insertApplication(eventId, earlyPending.id(), "pending", 40);
        insertApplication(eventId, waiting.id(), "waitlisted", 30);

        decide(owner, communityId, eventId, earlyPending.id(), "approved")
                .andExpect(jsonPath("$.status").value("waitlisted"))
                .andExpect(jsonPath("$.waitlistPosition").value(2));
        assertThat(applicationStatus(eventId, waiting.id())).isEqualTo("waitlisted");
    }

    /**
     * 【何のため】承認待ちの人は、締め切り後でも取り消せて、そのときお知らせは送らないことを確かめる。
     * 開始したイベントの申込みは、承認・却下できないこと。
     */
    @Test
    void 承認待ちは締め切り後でもお知らせなしで取り消せる() throws Exception {
        Account owner = newAccount();
        Account pending = newAccount();
        Account late = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long closedEvent = insertEvent(communityId, "締め切り後", 10, -1, 24, 26);
        insertApplication(closedEvent, pending.id(), "pending", 10);

        mockMvc.perform(as(pending, delete(eventUrl(communityId, closedEvent) + "/my-application")))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications WHERE event_id = ?", Integer.class,
                closedEvent)).isZero();

        long started = insertEvent(communityId, "開始済み", 10, -3, -1, 2);
        insertApplication(started, late.id(), "pending", 10);
        decide(owner, communityId, started, late.id(), "approved")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_STARTED"));
    }

    // ---------- 承認の方法を変えたとき ----------

    /**
     * 【何のため】手動から自動に変えると、承認待ちが申し込んだ順にまとめて承認され、定員を超えた分はキャンセル待ちになることを確かめる。
     * メンバー以外を「受け付けない」に変えても、メンバー以外の承認待ちは取り消されないこと。
     */
    @Test
    void 手動から自動に変えると承認待ちをまとめて承認する() throws Exception {
        Account owner = newAccount();
        Account first = newAccount();
        Account second = newAccount();
        Account outsider = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, first.id(), "member");
        insertMember(communityId, second.id(), "member");
        long eventId = insertEventWithApproval(communityId, 1, "manual", "manual");
        insertApplication(eventId, second.id(), "pending", 10);
        insertApplication(eventId, first.id(), "pending", 20);
        insertApplication(eventId, outsider.id(), "pending", 30);

        mockMvc.perform(as(owner, patch(eventUrl(communityId, eventId)), input(1, "auto", "closed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberApproval").value("auto"))
                .andExpect(jsonPath("$.nonMemberApproval").value("closed"))
                .andExpect(jsonPath("$.pendingCount").value(1));
        // 先に申し込んだ first が申込み済み、second はキャンセル待ち。メンバー以外は承認待ちのまま
        assertThat(applicationStatus(eventId, first.id())).isEqualTo("applied");
        assertThat(applicationStatus(eventId, second.id())).isEqualTo("waitlisted");
        assertThat(applicationStatus(eventId, outsider.id())).isEqualTo("pending");
        assertThat(notificationTypes(first.id(), eventId)).containsExactly("application_approved");

        // 承認の方法を送らなければ、今のまま
        mockMvc.perform(as(owner, patch(eventUrl(communityId, eventId)), input(2, null, null)))
                .andExpect(jsonPath("$.memberApproval").value("auto"))
                .andExpect(jsonPath("$.nonMemberApproval").value("closed"));
        // メンバー以外を自動に変えると、メンバー以外の承認待ちも承認される
        mockMvc.perform(as(owner, patch(eventUrl(communityId, eventId)), input(2, "auto", "auto")))
                .andExpect(jsonPath("$.pendingCount").value(0));
        assertThat(applicationStatus(eventId, outsider.id())).isEqualTo("waitlisted");
    }

    /**
     * 【何のため】イベントを削除したとき、承認待ちの人にもお知らせが届き、却下された人には届かないことを確かめる。
     */
    @Test
    void 削除のお知らせは承認待ちにも届き却下された人には届かない() throws Exception {
        Account owner = newAccount();
        Account pending = newAccount();
        Account rejected = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long eventId = insertEventWithApproval(communityId, 10, "manual", "closed");
        insertApplication(eventId, pending.id(), "pending", 20);
        insertApplication(eventId, rejected.id(), "rejected", 10);

        mockMvc.perform(as(owner, delete(eventUrl(communityId, eventId)))).andExpect(status().isNoContent());
        List<Long> notified = jdbcTemplate.queryForList(
                "SELECT user_id FROM notifications WHERE type = 'event_deleted' AND community_id = ?", Long.class,
                communityId);
        assertThat(notified).containsExactly(pending.id());
    }
}
