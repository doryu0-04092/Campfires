package com.campfires.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.campfires.ApiTestBase;

/**
 * 【役割】お知らせの API（一覧、未読の件数、既読）のテスト。
 * 【なぜ必要か】要件定義書 5.10 のルールと、他人あてのお知らせに触れられないことを確かめるため。
 */
class NotificationApiTest extends ApiTestBase {

    /** 【何のため】お知らせを1件作り、番号を返す。actorId が null なら、出来事を起こした人なし。 */
    private long insertNotification(long userId, String type, long communityId, Long eventId, Long actorId,
            int minutesAgo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notifications (user_id, type, event_title, community_id, event_id, actor_user_id, created_at)
                VALUES (?, ?, 'お知らせのイベント', ?, ?, ?, now() - make_interval(mins => ?))
                RETURNING id
                """, Long.class, userId, type, communityId, eventId, actorId, minutesAgo);
    }

    /**
     * 【何のため】一覧は自分あてのものだけを新しい順に返し、未読の件数と、出来事を起こした人（ニックネームだけ）が入ることを確かめる。
     */
    @Test
    void 一覧は自分あてのお知らせを新しい順に返す() throws Exception {
        Account me = newAccount();
        Account actor = newAccount();
        Account other = newAccount();
        long communityId = insertCommunity(me.id(), false);
        long eventId = insertEvent(communityId, "イベント", 10, 24, 48, 50);
        long older = insertNotification(me.id(), "late_cancellation", communityId, eventId, actor.id(), 20);
        long newer = insertNotification(me.id(), "event_deleted", communityId, null, null, 10);
        insertNotification(other.id(), "event_deleted", communityId, null, null, 5);

        mockMvc.perform(get("/api/notifications").session(me.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newer))
                .andExpect(jsonPath("$.items[0].eventId").isEmpty())
                .andExpect(jsonPath("$.items[0].actor").isEmpty())
                .andExpect(jsonPath("$.items[1].id").value(older))
                .andExpect(jsonPath("$.items[1].eventId").value(eventId))
                .andExpect(jsonPath("$.items[1].actor.userId").value(actor.id()))
                .andExpect(jsonPath("$.items[1].actor.nickname").value("たろう"))
                .andExpect(jsonPath("$.items[1].read").value(false));
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    /**
     * 【何のため】1件ずつ既読にでき、何度呼んでも成功し、他人あてのお知らせは 404 になることを確かめる。
     */
    @Test
    void お知らせを1件ずつ既読にできる() throws Exception {
        Account me = newAccount();
        Account other = newAccount();
        long communityId = insertCommunity(me.id(), false);
        long mine = insertNotification(me.id(), "event_deleted", communityId, null, null, 10);
        insertNotification(me.id(), "event_deleted", communityId, null, null, 5);
        long others = insertNotification(other.id(), "event_deleted", communityId, null, null, 5);

        mockMvc.perform(as(me, put("/api/notifications/" + mine + "/read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine))
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(as(me, put("/api/notifications/" + mine + "/read"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").session(me.session()))
                .andExpect(jsonPath("$.count").value(1));

        // 他人あてのお知らせは、ないものとして 404。既読にもならない
        mockMvc.perform(as(me, put("/api/notifications/" + others + "/read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/notifications/unread-count").session(other.session()))
                .andExpect(jsonPath("$.count").value(1));
    }

    /**
     * 【何のため】すべて既読にすると、自分あての未読が 0 になり、他人あてのお知らせは変わらないことを確かめる。
     */
    @Test
    void すべて既読にできる() throws Exception {
        Account me = newAccount();
        Account other = newAccount();
        long communityId = insertCommunity(me.id(), false);
        insertNotification(me.id(), "event_deleted", communityId, null, null, 10);
        insertNotification(me.id(), "event_deleted", communityId, null, null, 5);
        insertNotification(other.id(), "event_deleted", communityId, null, null, 5);

        mockMvc.perform(as(me, post("/api/notifications/read-all"))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications").session(me.session()))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true))
                .andExpect(jsonPath("$.items[1].read").value(true));
        mockMvc.perform(get("/api/notifications/unread-count").session(other.session()))
                .andExpect(jsonPath("$.count").value(1));
        // 未読がなくても成功する
        mockMvc.perform(as(me, post("/api/notifications/read-all"))).andExpect(status().isNoContent());
    }
}
