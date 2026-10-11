package com.campfires.common.security;

import static com.campfires.CsrfTestSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import com.campfires.ApiTestBase;
import com.campfires.common.web.SpaConfig;

/**
 * 【役割】セキュリティの見直しで確かめた点を、攻撃の形の通信で確かめるテスト。
 * 【なぜ必要か】OWASP Top 10 などでよく知られた攻撃（なりすまし、ほかの人のデータの操作、SQL インジェクション、
 * CSRF、パストラバーサル、エラーからの情報漏えいなど）に対して、正しく断られることを確かめ、
 * 後からコードを変えたときに、守りが外れていないかを毎回確かめられるようにするため（docs/security.md）。
 */
class SecurityReviewTest extends ApiTestBase {

    // ---------- 入力の扱い ----------

    /**
     * 【何のため】送るべきでない項目（メールアドレス、本名、番号）を送っても、書き換えられないことを確かめる（マスアサインメント）。
     * 受け取る項目は、それぞれの API の record（UpdateMeRequest など）に書いた項目だけ。
     */
    @Test
    void 送るべきでない項目を送っても書き換えられない() throws Exception {
        Account me = newAccount();
        mockMvc.perform(as(me, patch("/api/me"), Map.of("nickname", "じろう", "email", "attacker@example.com",
                "realName", "なりすまし", "id", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("じろう"))
                .andExpect(jsonPath("$.email").value(me.email()))
                .andExpect(jsonPath("$.realName").value("山田 太郎"))
                .andExpect(jsonPath("$.id").value(me.id()));
    }

    /**
     * 【何のため】SQL で特別な意味を持つ文字（' -- % _ \ ;）で検索しても、エラーにならず、
     * 文字そのものとして探されることを確かめる（SQL インジェクション。SQL の値は、すべて #{} で渡している）。
     */
    @Test
    void SQLの特別な文字で検索しても文字として扱う() throws Exception {
        for (String q : new String[] { "' OR 1=1 --", "%", "_", "\\", "'; DROP TABLE users; --" }) {
            mockMvc.perform(get("/api/communities").param("q", q))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0));
            mockMvc.perform(get("/api/events").param("q", q))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0));
        }
        // テーブルは消えていない
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isPositive();
    }

    /**
     * 【何のため】形の違うデータ（文字の代わりにオブジェクト）や、壊れた JSON を送っても 400 になり、
     * 返事に、プログラムの内部の情報（クラスの名前、Jackson のエラーの文）が入らないことを確かめる（情報漏えいの対策）。
     */
    @Test
    void 形の違うデータを送っても内部の情報を返さない() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":{\"$ne\":null},\"password\":[\"x\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("jackson"))))
                .andExpect(content().string(not(containsString("java."))));
        mockMvc.perform(post("/api/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("送られてきたデータの形が正しくありません。"));
    }

    // ---------- CSRF と、ほかのサイトからの通信 ----------

    /**
     * 【何のため】CSRF トークンのないログアウトは断られることを確かめる（ほかのサイトに置かれた罠から、勝手にログアウトさせられない）。
     */
    @Test
    void CSRFトークンのないログアウトは断る() throws Exception {
        Account me = newAccount();
        mockMvc.perform(post("/api/auth/logout").session(me.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(get("/api/me").session(me.session())).andExpect(status().isOk());
    }

    /**
     * 【何のため】ほかのサイト（オリジン）からの通信に、読んでよいという許可（CORS のヘッダー）を返さないことを確かめる。
     * 画面と API は同じ場所にあるので、CORS は使わない。許可がないので、ほかのサイトの JavaScript は、返事を読めない。
     */
    @Test
    void ほかのサイトからの通信には読んでよい許可を返さない() throws Exception {
        mockMvc.perform(options("/api/me").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mockMvc.perform(get("/api/communities").header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---------- 権限（ほかの人のデータを操作できない） ----------

    /**
     * 【何のため】運営スタッフは、オーナーだけの操作（コミュニティの編集・アーカイブ、任命、除名）ができないことを確かめる。
     */
    @Test
    void 運営スタッフはオーナーだけの操作ができない() throws Exception {
        Account owner = newAccount();
        Account staff = newAccount();
        Account member = newAccount();
        long communityId = insertCommunity(owner.id(), false);
        insertMember(communityId, staff.id(), "staff");
        insertMember(communityId, member.id(), "member");
        String base = "/api/communities/" + communityId;

        mockMvc.perform(as(staff, patch(base), Map.of("name", "乗っ取り", "description", "")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(staff, put(base + "/archive"))).andExpect(status().isForbidden());
        mockMvc.perform(as(staff, patch(base + "/members/" + member.id()), Map.of("role", "staff")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(staff, delete(base + "/members/" + member.id()))).andExpect(status().isForbidden());
    }

    /**
     * 【何のため】URL の番号を書き換えても、ほかのコミュニティのイベントや申込みを操作できないことを確かめる（IDOR の対策）。
     * 自分が管理するコミュニティの番号に、ほかのコミュニティのイベントの番号を組み合わせても、見つからない（404）。
     */
    @Test
    void URLの番号を書き換えてもほかのコミュニティを操作できない() throws Exception {
        Account myOwner = newAccount();
        Account otherOwner = newAccount();
        Account applicant = newAccount();
        long mine = insertCommunity(myOwner.id(), false);
        long others = insertCommunity(otherOwner.id(), false);
        long othersEvent = insertEvent(others, "ほかのコミュニティのイベント", 10, 24, 48, 50);
        insertApplication(othersEvent, applicant.id(), "pending", 10);
        String crossed = "/api/communities/" + mine + "/events/" + othersEvent;

        mockMvc.perform(get(crossed + "/applications").session(myOwner.session())).andExpect(status().isNotFound());
        mockMvc.perform(as(myOwner, patch(crossed + "/applications/" + applicant.id()), Map.of("status", "approved")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(myOwner, delete(crossed))).andExpect(status().isNotFound());
        assertThat(applicationStatus(othersEvent, applicant.id())).isEqualTo("pending");
    }

    // ---------- 画面のファイル ----------

    /**
     * 【何のため】画面のファイルを返す仕組みが、../ で static/ の外のファイル（設定ファイルなど）を返さないことを確かめる
     * （パストラバーサルの対策）。SpaConfig の部品を、直接呼んで確かめる。
     */
    @Test
    void 画面のファイルを返す仕組みはstaticの外のファイルを返さない() throws Exception {
        var resolver = new SpaConfig.SpaResourceResolver();
        Resource location = new ClassPathResource("static/");
        assertThat(resolver.find("../application.properties", location)).isNull();
        assertThat(resolver.find("..%2fapplication.properties", location)).isNull();
        assertThat(resolver.find("../db/migration/V1__create_tables.sql", location)).isNull();
        // static/ の中のファイルは返す。画面の URL には、画面の入り口を返す
        assertThat(resolver.find("index.html", location)).isNotNull();
        assertThat(resolver.find("communities/1", location).getFilename()).isEqualTo("index.html");
    }
}
