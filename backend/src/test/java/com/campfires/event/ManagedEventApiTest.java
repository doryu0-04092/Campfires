package com.campfires.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.campfires.ApiTestBase;

/**
 * 【役割】管理しているイベントの一覧の API（GET /api/me/managed-events）のテスト（Issue #45）。
 * 【なぜ必要か】左のメニューの「イベント編集」「申込者一覧」で選べるイベントが、
 * 自分がオーナーか運営スタッフをしているコミュニティのものだけであることを確かめるため。
 */
class ManagedEventApiTest extends ApiTestBase {

    /**
     * 【何のため】オーナーと運営スタッフのコミュニティのイベントだけが、開催前・終了済みに分けて、決まった順で返ることを確かめる。
     * メンバーとしてだけ参加しているコミュニティのイベントは、返らないこと。
     */
    @Test
    void 管理しているコミュニティのイベントだけを返す() throws Exception {
        Account me = newAccount();
        Account other = newAccount();
        long owned = insertCommunity(me.id(), false);
        long staffed = insertCommunity(other.id(), true);
        insertMember(staffed, me.id(), "staff");
        long memberOnly = insertCommunity(other.id(), false);
        insertMember(memberOnly, me.id(), "member");

        long later = insertEvent(owned, "後", 10, 24, 72, 74);
        long sooner = insertEvent(staffed, "先", 10, 24, 48, 50);
        insertEvent(memberOnly, "メンバーとしてだけ", 10, 24, 30, 32);
        long past = insertEvent(owned, "終了", 10, -50, -48, -46);
        insertApplication(later, other.id(), "pending", 10);

        mockMvc.perform(get("/api/me/managed-events").session(me.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value(sooner))
                .andExpect(jsonPath("$.items[0].myRole").value("staff"))
                .andExpect(jsonPath("$.items[1].id").value(later))
                .andExpect(jsonPath("$.items[1].myRole").value("owner"))
                .andExpect(jsonPath("$.items[1].pendingCount").value(1))
                .andExpect(jsonPath("$.items[1].communityName").value("テストのコミュニティ"));
        mockMvc.perform(get("/api/me/managed-events").param("status", "past").session(me.session()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(past));
        mockMvc.perform(get("/api/me/managed-events")).andExpect(status().isUnauthorized());
    }
}
