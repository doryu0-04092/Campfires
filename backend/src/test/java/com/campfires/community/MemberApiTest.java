package com.campfires.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.campfires.ApiTestBase;

/**
 * 【役割】参加申請とメンバーの API のテスト。
 * 【なぜ必要か】参加申請（要件定義書 5.1）、退会（5.4）、除名（5.4.1）のルールと、
 * 権限（要件定義書 3章の表：オーナーだけ、オーナーと運営スタッフだけ）のとおりに動くことを確かめるため。
 */
class MemberApiTest extends ApiTestBase {

    /** 【何のため】コミュニティを作って、番号を返す。 */
    private long createCommunity(Account owner) throws Exception {
        String body = mockMvc.perform(as(owner, post("/api/communities"), Map.of("name", "メンバーのテスト", "description", "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asLong();
    }

    /** 【何のため】参加を申請して、申請の番号を返す。 */
    private long requestJoin(Account user, long communityId) throws Exception {
        String body = mockMvc.perform(as(user, post("/api/communities/" + communityId + "/join-requests")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asLong();
    }

    /** 【何のため】参加を申請して、オーナーが承認する（メンバーにする）。 */
    private void join(Account owner, Account user, long communityId) throws Exception {
        long requestId = requestJoin(user, communityId);
        mockMvc.perform(as(owner, patch("/api/communities/" + communityId + "/join-requests/" + requestId),
                Map.of("status", "approved")))
                .andExpect(status().isOk());
    }

    // ---------- 参加申請 ----------

    /**
     * 【何のため】申請 → 一覧 → 承認 → メンバーになる、の流れと、そのときのエラーを確かめる。
     */
    @Test
    void 参加を申請して承認されるとメンバーになる() throws Exception {
        Account owner = newAccount();
        Account user = newAccount();
        long id = createCommunity(owner);

        long requestId = requestJoin(user, id);
        // 申請中に、もう一度申請すると 409
        mockMvc.perform(as(user, post("/api/communities/" + id + "/join-requests")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_PENDING"));
        mockMvc.perform(get("/api/communities/" + id).session(user.session()))
                .andExpect(jsonPath("$.myJoinRequestStatus").value("pending"));

        // 申請の一覧は、オーナーと運営スタッフだけが見られる
        mockMvc.perform(get("/api/communities/" + id + "/join-requests").session(user.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/communities/" + id + "/join-requests").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].user.userId").value(user.id()))
                .andExpect(jsonPath("$.items[0].user.email").doesNotExist());
        mockMvc.perform(get("/api/me/managed-communities").session(owner.session()))
                .andExpect(jsonPath("$.items[0].pendingJoinRequestCount").value(1));

        // 正しくない状態は 400
        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/join-requests/" + requestId),
                Map.of("status", "pending")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        // 承認する
        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/join-requests/" + requestId),
                Map.of("status", "approved")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        // もう一度処理すると 409
        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/join-requests/" + requestId),
                Map.of("status", "rejected")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_ALREADY_DECIDED"));

        // メンバーになった
        mockMvc.perform(get("/api/communities/" + id).session(user.session()))
                .andExpect(jsonPath("$.myRole").value("member"))
                .andExpect(jsonPath("$.myJoinRequestStatus").isEmpty())
                .andExpect(jsonPath("$.memberCount").value(2));
        mockMvc.perform(get("/api/me/communities").session(user.session()))
                .andExpect(jsonPath("$.items[0].id").value(id));
        mockMvc.perform(as(user, post("/api/communities/" + id + "/join-requests")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    /**
     * 【何のため】却下された人は、もう一度申請できることを確かめる。ない申請は 404 になること。
     */
    @Test
    void 却下されてももう一度申請できる() throws Exception {
        Account owner = newAccount();
        Account user = newAccount();
        long id = createCommunity(owner);
        long requestId = requestJoin(user, id);
        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/join-requests/" + requestId),
                Map.of("status", "rejected")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/communities/" + id).session(user.session()))
                .andExpect(jsonPath("$.myJoinRequestStatus").value("rejected"));
        requestJoin(user, id);

        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/join-requests/999999999"),
                Map.of("status", "approved")))
                .andExpect(status().isNotFound());
    }

    // ---------- メンバーの管理 ----------

    /**
     * 【何のため】メンバーの一覧の並び順と、運営スタッフの任命・解除の権限を確かめる。
     */
    @Test
    void 運営スタッフの任命はオーナーだけができる() throws Exception {
        Account owner = newAccount();
        Account staff = newAccount();
        Account member = newAccount();
        long id = createCommunity(owner);
        join(owner, member, id);
        join(owner, staff, id);

        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/members/" + staff.id()), Map.of("role", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("staff"));

        // 立場の順（オーナー → 運営スタッフ → メンバー）
        mockMvc.perform(get("/api/communities/" + id + "/members").session(staff.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].user.userId").value(owner.id()))
                .andExpect(jsonPath("$.items[1].user.userId").value(staff.id()))
                .andExpect(jsonPath("$.items[2].user.userId").value(member.id()));
        mockMvc.perform(get("/api/communities/" + id + "/members").session(member.session()))
                .andExpect(status().isForbidden());

        // 運営スタッフは、立場を変えられない。オーナーの立場は変えられない
        mockMvc.perform(as(staff, patch("/api/communities/" + id + "/members/" + member.id()), Map.of("role", "staff")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/members/" + owner.id()), Map.of("role", "member")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWNER"));
        mockMvc.perform(as(owner, patch("/api/communities/" + id + "/members/999999999"), Map.of("role", "staff")))
                .andExpect(status().isNotFound());
    }

    /**
     * 【何のため】除名のルール（要件定義書 5.4.1）を確かめる。
     * オーナーだけができ、除名された人の、開催前のイベントへの申込みは取り消される。
     */
    @Test
    void 除名すると開催前のイベントの申込みが取り消される() throws Exception {
        Account owner = newAccount();
        Account member = newAccount();
        long id = createCommunity(owner);
        join(owner, member, id);
        long eventId = jdbcTemplate.queryForObject("""
                INSERT INTO events (community_id, title, start_at, end_at, application_deadline, location, capacity)
                VALUES (?, 'イベント', now() + interval '2 days', now() + interval '3 days', now() + interval '1 day', '会場', 10)
                RETURNING id
                """, Long.class, id);
        jdbcTemplate.update("INSERT INTO applications (event_id, user_id, status, queued_at) VALUES (?, ?, 'applied', now())",
                eventId, member.id());

        mockMvc.perform(as(member, delete("/api/communities/" + id + "/members/" + owner.id())))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(owner, delete("/api/communities/" + id + "/members/" + owner.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWNER"));
        mockMvc.perform(as(owner, delete("/api/communities/" + id + "/members/" + member.id())))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM applications WHERE event_id = ?", Integer.class,
                eventId)).isZero();
        mockMvc.perform(get("/api/communities/" + id).session(member.session()))
                .andExpect(jsonPath("$.myRole").isEmpty())
                // 承認済みの申請はあるが、メンバーではないので、申請していない扱い
                .andExpect(jsonPath("$.myJoinRequestStatus").isEmpty());
        // 除名された人も、もう一度申請できる
        requestJoin(member, id);
    }

    /**
     * 【何のため】退会のルール（要件定義書 5.4）を確かめる。オーナーは退会できず、メンバーでない人は 409。
     */
    @Test
    void 退会できるのはメンバーと運営スタッフ() throws Exception {
        Account owner = newAccount();
        Account member = newAccount();
        Account stranger = newAccount();
        long id = createCommunity(owner);
        join(owner, member, id);

        mockMvc.perform(as(owner, delete("/api/communities/" + id + "/membership")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
        mockMvc.perform(as(stranger, delete("/api/communities/" + id + "/membership")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));
        mockMvc.perform(as(member, delete("/api/communities/" + id + "/membership")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/communities").session(member.session()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
