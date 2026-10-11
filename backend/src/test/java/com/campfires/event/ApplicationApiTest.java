package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.campfires.ApiTestBase;

/**
 * 【役割】イベントへの申込みの API（申込み、キャンセル、申込者の一覧、自分が申し込んだイベント）のテスト。
 * 【なぜ必要か】申込みのルール（要件定義書 5.2）、キャンセル待ちと繰り上げ（5.3、F-15、F-16）、
 * 締め切り後のキャンセルのお知らせ（5.10）のとおりに動くことを確かめるため。
 */
class ApplicationApiTest extends ApiTestBase {

    private static String applyUrl(long communityId, long eventId) {
        return "/api/communities/" + communityId + "/events/" + eventId + "/applications";
    }

    private static String cancelUrl(long communityId, long eventId) {
        return "/api/communities/" + communityId + "/events/" + eventId + "/my-application";
    }

    // ---------- 申込み ----------

    /**
     * 【何のため】空きがあれば申込み済み、満席ならキャンセル待ちになり、何番目かが分かることを確かめる。
     * メンバーでない人、二重の申込み、締め切り後の申込みは、エラーになること。
     */
    @Test
    void 満席ならキャンセル待ちになる() throws Exception {
        Account owner = newAccount();
        Account first = newAccount();
        Account second = newAccount();
        Account third = newAccount();
        Account stranger = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        for (Account member : List.of(first, second, third)) {
            insertMember(communityId, member.id(), "member");
        }
        long eventId = insertEvent(communityId, "申込み", 1, 24, 48, 50);

        mockMvc.perform(as(stranger, post(applyUrl(communityId, eventId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        mockMvc.perform(as(first, post(applyUrl(communityId, eventId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("applied"))
                .andExpect(jsonPath("$.waitlistPosition").isEmpty());
        mockMvc.perform(as(first, post(applyUrl(communityId, eventId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
        mockMvc.perform(as(second, post(applyUrl(communityId, eventId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("waitlisted"))
                .andExpect(jsonPath("$.waitlistPosition").value(1));
        mockMvc.perform(as(third, post(applyUrl(communityId, eventId))))
                .andExpect(jsonPath("$.waitlistPosition").value(2));

        // 詳細と、自分が申し込んだイベントの一覧にも、申込みの状況が出る
        mockMvc.perform(get("/api/communities/" + communityId + "/events/" + eventId).session(third.session()))
                .andExpect(jsonPath("$.remainingSeats").value(0))
                .andExpect(jsonPath("$.waitlistCount").value(2))
                .andExpect(jsonPath("$.myRole").value("member"))
                .andExpect(jsonPath("$.myApplication.status").value("waitlisted"))
                .andExpect(jsonPath("$.myApplication.waitlistPosition").value(2));
        mockMvc.perform(get("/api/me/events").session(third.session()))
                .andExpect(jsonPath("$.items[0].id").value(eventId))
                .andExpect(jsonPath("$.items[0].myApplication.waitlistPosition").value(2));

        // 締め切り後は申し込めない
        long closed = insertEvent(communityId, "締め切り後", 10, -1, 24, 26);
        mockMvc.perform(as(first, post(applyUrl(communityId, closed))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_DEADLINE_PASSED"));
    }

    /**
     * 【何のため】自分が申し込んだイベントの一覧は、開催前のものだけを、開始日時の近い順に返すことを確かめる。
     */
    @Test
    void 自分が申し込んだイベントは開催前のものだけを開始日時の近い順に返す() throws Exception {
        Account owner = newAccount();
        Account me = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, me.id(), "member");
        long later = insertEvent(communityId, "後", 10, 24, 72, 74);
        long sooner = insertEvent(communityId, "先", 10, 24, 48, 50);
        long ended = insertEvent(communityId, "終了", 10, -50, -48, -46);
        for (long eventId : List.of(later, sooner, ended)) {
            insertApplication(eventId, me.id(), "applied", 10);
        }

        mockMvc.perform(get("/api/me/events").session(me.session()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(sooner))
                .andExpect(jsonPath("$.items[1].id").value(later))
                .andExpect(jsonPath("$.items[0].myApplication.status").value("applied"));
    }

    // ---------- キャンセル ----------

    /**
     * 【何のため】締め切り前に申込み済みの人が取り消すと、キャンセル待ちの先頭の人が繰り上がることを確かめる（F-16）。
     */
    @Test
    void 締め切り前のキャンセルでキャンセル待ちが繰り上がる() throws Exception {
        Account owner = newAccount();
        Account applied = newAccount();
        Account first = newAccount();
        Account second = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        long eventId = insertEvent(communityId, "繰り上げ", 1, 24, 48, 50);
        insertApplication(eventId, applied.id(), "applied", 30);
        insertApplication(eventId, first.id(), "waitlisted", 20);
        insertApplication(eventId, second.id(), "waitlisted", 10);

        mockMvc.perform(as(applied, delete(cancelUrl(communityId, eventId)))).andExpect(status().isNoContent());
        assertThat(applicationStatus(eventId, first.id())).isEqualTo("applied");
        assertThat(applicationStatus(eventId, second.id())).isEqualTo("waitlisted");

        mockMvc.perform(as(applied, delete(cancelUrl(communityId, eventId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_APPLIED"));
        // 締め切り前のキャンセルでは、お知らせは送らない
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications WHERE event_id = ?", Integer.class,
                eventId)).isZero();
    }

    /**
     * 【何のため】締め切り後に取り消すと、繰り上げはせず、オーナーと運営スタッフにお知らせが届くことを確かめる。
     * 終了したイベントは取り消せないこと。
     */
    @Test
    void 締め切り後のキャンセルはオーナーと運営スタッフに知らせる() throws Exception {
        Account owner = newAccount();
        Account staff = newAccount();
        Account applied = newAccount();
        Account waiting = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, staff.id(), "staff");
        long eventId = insertEvent(communityId, "締め切り後", 1, -1, 24, 26);
        insertApplication(eventId, applied.id(), "applied", 30);
        insertApplication(eventId, waiting.id(), "waitlisted", 20);

        mockMvc.perform(as(applied, delete(cancelUrl(communityId, eventId)))).andExpect(status().isNoContent());
        assertThat(applicationStatus(eventId, waiting.id())).isEqualTo("waitlisted");
        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "SELECT user_id, actor_user_id FROM notifications WHERE event_id = ? AND type = 'late_cancellation'",
                eventId);
        assertThat(notifications).extracting(n -> n.get("user_id")).containsExactlyInAnyOrder(owner.id(), staff.id());
        assertThat(notifications).allSatisfy(n -> assertThat(n.get("actor_user_id")).isEqualTo(applied.id()));

        long ended = insertEvent(communityId, "終了", 10, -50, -48, -46);
        insertApplication(ended, applied.id(), "applied", 10);
        mockMvc.perform(as(applied, delete(cancelUrl(communityId, ended))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_ENDED"));
    }

    // ---------- 申込者の一覧 ----------

    /**
     * 【何のため】申込者の一覧は、オーナーと運営スタッフだけが見られ、
     * 申込み済み → キャンセル待ち（順番どおり）の順に、ニックネームだけを返すことを確かめる。
     */
    @Test
    void 申込者の一覧は申込み済みのあとにキャンセル待ちを順番どおりに並べる() throws Exception {
        Account owner = newAccount();
        Account member = newAccount();
        Account waiting = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, member.id(), "member");
        long eventId = insertEvent(communityId, "申込者", 1, 24, 48, 50);
        insertApplication(eventId, waiting.id(), "waitlisted", 20);
        insertApplication(eventId, member.id(), "applied", 10);

        mockMvc.perform(get(applyUrl(communityId, eventId)).session(member.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(applyUrl(communityId, eventId)).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].user.userId").value(member.id()))
                .andExpect(jsonPath("$.items[0].status").value("applied"))
                .andExpect(jsonPath("$.items[0].waitlistPosition").isEmpty())
                .andExpect(jsonPath("$.items[1].user.userId").value(waiting.id()))
                .andExpect(jsonPath("$.items[1].waitlistPosition").value(1))
                .andExpect(jsonPath("$.items[1].user.nickname").value("たろう"))
                .andExpect(jsonPath("$.items[1].user.realName").doesNotExist());
    }
}
