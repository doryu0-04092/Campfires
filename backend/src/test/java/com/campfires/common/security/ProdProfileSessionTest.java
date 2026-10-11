package com.campfires.common.security;

import static com.campfires.CsrfTestSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.campfires.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/**
 * 【役割】本番の設定（prod のプロファイル）で起動し、ログインの状態がデータベースに保存されることを確かめるテスト（Issue #47）。
 * 【なぜ必要か】本番では、箱を入れ替えたり増やしたりしてもログインが切れないように、
 * セッションをデータベース（spring_session テーブル）に置く。その設定が正しく効いているかを確かめる。
 *
 * prod の設定はデータベースのつなぎ先を環境変数（DB_HOST など）で受け取るので、テストでは仮の値を入れておく。
 * 実際のつなぎ先は、Testcontainers のデータベース（@ServiceConnection）が優先される。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "DB_HOST=unused", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "campfires.rate-limit.enabled=false" })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileSessionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    /**
     * 【何のため】アカウントを登録すると、セッションがデータベースに保存され、その Cookie でログインした状態が続くことを確かめる。
     */
    @Test
    void ログインの状態がデータベースに保存される() throws Exception {
        String email = "prod-" + UUID.randomUUID() + "@example.com";
        MvcResult signup = mockMvc.perform(post("/api/auth/signup").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("realName", "山田 太郎", "nickname", "たろう",
                        "email", email, "password", "campfire2026", "agreedToPolicies", true))))
                .andExpect(status().isCreated())
                .andReturn();

        // セッションの Cookie（名前は開発中と同じ JSESSIONID）が返る
        Cookie sessionCookie = signup.getResponse().getCookie("JSESSIONID");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getSecure()).isTrue();
        // セッションがデータベースに保存されている
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isPositive();

        // その Cookie を送ると、ログインした状態として扱われる（箱の中のメモリではなく、データベースから読んでいる）
        mockMvc.perform(get("/api/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }
}
