package com.campfires.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.campfires.ApiTestBase;

/**
 * 【役割】コミュニティの API（作成、詳細、一覧・検索、編集、アーカイブ）のテスト。
 * 【なぜ必要か】API 仕様書（docs/api/openapi.yaml の communities）と、要件定義書 5.9（アーカイブ）、
 * 5.11（一覧と検索）のルールのとおりに動くことを確かめるため。
 */
class CommunityApiTest extends ApiTestBase {

    /** 【何のため】コミュニティを作って、番号を返す。 */
    private long createCommunity(Account owner, String name, String description) throws Exception {
        String body = mockMvc.perform(as(owner, post("/api/communities"),
                Map.of("name", name, "description", description)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asLong();
    }

    // ---------- 作成と詳細 ----------

    /**
     * 【何のため】作ると 201 と Location が返り、作った人がオーナーになることを確かめる。
     */
    @Test
    void コミュニティを作るとオーナーになる() throws Exception {
        Account owner = newAccount();
        mockMvc.perform(as(owner, post("/api/communities"), Map.of("name", " Java 勉強会 ", "description", "説明")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/communities/")))
                .andExpect(jsonPath("$.name").value("Java 勉強会"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.owner.userId").value(owner.id()))
                .andExpect(jsonPath("$.owner.nickname").value("たろう"))
                .andExpect(jsonPath("$.archivedAt").isEmpty())
                .andExpect(jsonPath("$.myRole").value("owner"))
                .andExpect(jsonPath("$.myJoinRequestStatus").isEmpty());
    }

    /**
     * 【何のため】名前が空なら 400、ログインしていなければ 401 になることを確かめる。
     */
    @Test
    void 名前が空なら作れない() throws Exception {
        Account owner = newAccount();
        mockMvc.perform(as(owner, post("/api/communities"), Map.of("name", " ", "description", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        mockMvc.perform(withJson(post("/api/communities"), Map.of("name", "名前", "description", "")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 【何のため】詳細は、ログインしていなくても見られること。ない番号や、数でない番号は 404 になることを確かめる。
     */
    @Test
    void 詳細はログインしていなくても見られる() throws Exception {
        Account owner = newAccount();
        long id = createCommunity(owner, "詳細のテスト", "");
        mockMvc.perform(get("/api/communities/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").isEmpty())
                // 本名とメールアドレスは、ほかの人に返さない
                .andExpect(jsonPath("$.owner.realName").doesNotExist())
                .andExpect(jsonPath("$.owner.email").doesNotExist());
        mockMvc.perform(get("/api/communities/999999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/communities/abc")).andExpect(status().isNotFound());
    }

    // ---------- 一覧と検索 ----------

    /**
     * 【何のため】検索のルール（要件定義書 5.11）を確かめる。
     * - 全角の空白で区切った単語も使える。大文字と小文字は区別しない
     * - 当てはまった単語の数が多い順、同じなら作成日時の新しい順
     * - アーカイブ済みは含めない
     */
    @Test
    void 検索は当てはまった単語の数が多い順() throws Exception {
        Account owner = newAccount();
        String w1 = uniqueWord();
        String w2 = uniqueWord();
        long both = createCommunity(owner, w1 + " " + w2, "");
        long one = createCommunity(owner, w1, "");
        long bothNewer = createCommunity(owner, "名前", w2.toUpperCase() + " と " + w1);
        long archived = createCommunity(owner, w1 + " " + w2, "");
        mockMvc.perform(as(owner, put("/api/communities/" + archived + "/archive"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/communities").param("q", " " + w1 + "　" + w2 + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[0].id").value(bothNewer))
                .andExpect(jsonPath("$.items[1].id").value(both))
                .andExpect(jsonPath("$.items[2].id").value(one))
                .andExpect(jsonPath("$.limitExceeded").value(false));
    }

    /**
     * 【何のため】検索の結果は300件まで、1ページ30件で返し、上限を超えたら limitExceeded が true になることを確かめる。
     */
    @Test
    void 検索の結果は300件まで() throws Exception {
        Account owner = newAccount();
        String word = uniqueWord();
        // 301件のコミュニティを、SQL でまとめて作る（generate_series は 1〜301 の数の並びを作る関数）
        jdbcTemplate.update("""
                INSERT INTO communities (name, owner_id) SELECT ? || ' ' || n, ? FROM generate_series(1, 301) AS n
                """, word, owner.id());

        mockMvc.perform(get("/api/communities").param("q", word))
                .andExpect(jsonPath("$.totalCount").value(300))
                .andExpect(jsonPath("$.totalPages").value(10))
                .andExpect(jsonPath("$.pageSize").value(30))
                .andExpect(jsonPath("$.items.length()").value(30))
                .andExpect(jsonPath("$.limitExceeded").value(true));
        mockMvc.perform(get("/api/communities").param("q", word).param("page", "10"))
                .andExpect(jsonPath("$.items.length()").value(30));
        mockMvc.perform(get("/api/communities").param("q", word).param("page", "11"))
                .andExpect(jsonPath("$.items.length()").value(0));
        // 数でないページ番号は、1ページ目として扱う
        mockMvc.perform(get("/api/communities").param("q", word).param("page", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }

    // ---------- 編集とアーカイブ ----------

    /**
     * 【何のため】編集はオーナーだけができ、アーカイブ中はできないことを確かめる。
     */
    @Test
    void 編集はオーナーだけでアーカイブ中はできない() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long id = createCommunity(owner, "編集前", "");

        mockMvc.perform(as(other, patch("/api/communities/" + id), Map.of("name", "乗っ取り", "description", "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(as(owner, patch("/api/communities/" + id), Map.of("name", "編集後", "description", "説明")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("編集後"))
                .andExpect(jsonPath("$.description").value("説明"));

        mockMvc.perform(as(owner, put("/api/communities/" + id + "/archive"))).andExpect(status().isOk());
        mockMvc.perform(as(owner, patch("/api/communities/" + id), Map.of("name", "もう一度", "description", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_ARCHIVED"));
    }

    /**
     * 【何のため】アーカイブのルール（要件定義書 5.9）を確かめる。
     * - オーナーだけができる。何度呼んでも成功する
     * - 申請中の参加申請は、却下になる。新しい参加の申請はできない
     * - 元に戻せる。却下した申請は、元に戻らない
     */
    @Test
    void アーカイブすると申請中の参加申請が却下される() throws Exception {
        Account owner = newAccount();
        Account applicant = newAccount();
        Account other = newAccount();
        long id = createCommunity(owner, "アーカイブのテスト", "");
        mockMvc.perform(as(applicant, post("/api/communities/" + id + "/join-requests")))
                .andExpect(status().isCreated());

        mockMvc.perform(as(other, put("/api/communities/" + id + "/archive"))).andExpect(status().isForbidden());
        mockMvc.perform(as(owner, put("/api/communities/" + id + "/archive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isString());
        // 何度呼んでも成功する
        mockMvc.perform(as(owner, put("/api/communities/" + id + "/archive"))).andExpect(status().isOk());

        // 申請中だった申請は却下になり、新しい申請はできない
        mockMvc.perform(get("/api/communities/" + id).session(applicant.session()))
                .andExpect(jsonPath("$.myJoinRequestStatus").value("rejected"));
        mockMvc.perform(as(other, post("/api/communities/" + id + "/join-requests")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_ARCHIVED"));

        // 管理しているコミュニティの一覧には、アーカイブ済みも出る
        mockMvc.perform(get("/api/me/managed-communities").session(owner.session()))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].archivedAt").isString())
                .andExpect(jsonPath("$.items[0].myRole").value("owner"));

        // 元に戻す。却下した申請は、元に戻らない
        mockMvc.perform(as(owner, delete("/api/communities/" + id + "/archive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isEmpty());
        mockMvc.perform(get("/api/communities/" + id).session(applicant.session()))
                .andExpect(jsonPath("$.myJoinRequestStatus").value("rejected"));
    }

    // ---------- 削除したアカウントのセッション ----------

    /**
     * 【何のため】ほかの端末でアカウントを削除したとき、残っているセッションでは操作できない（401）ことを確かめる。
     * ログインなしで見られる API は、ログインしていない人として見られること。
     */
    @Test
    void 削除したアカウントのほかのセッションは使えない() throws Exception {
        Account me = newAccount();
        long communityId = createCommunity(newAccount(), "公開のテスト", "");
        // 同じアカウントで、別の端末（2台）からログインする
        MockHttpSession phone = loginOnAnotherDevice(me);
        MockHttpSession tablet = loginOnAnotherDevice(me);

        mockMvc.perform(as(me, delete("/api/me"), Map.of("password", PASSWORD))).andExpect(status().isNoContent());

        // ログインが必要な API は 401。セッションも消える
        mockMvc.perform(get("/api/me/communities").session(phone))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(phone.isInvalid()).isTrue();

        // ログインなしで見られる API は、ログインしていない人として見られる
        mockMvc.perform(get("/api/communities/" + communityId).session(tablet))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").isEmpty());
        assertThat(tablet.isInvalid()).isTrue();
    }

    /** 【何のため】別の端末からログインしたセッションを返す。 */
    private MockHttpSession loginOnAnotherDevice(Account account) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(withJson(post("/api/auth/login"), Map.of("email", account.email(), "password", PASSWORD))
                .session(session))
                .andExpect(status().isOk());
        return session;
    }
}
