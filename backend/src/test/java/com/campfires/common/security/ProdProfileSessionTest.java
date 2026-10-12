package com.campfires.common.security;

import static com.campfires.CsrfTestSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        // 30分操作がなければ、ログアウトになる（データベースに保存したセッションにも、同じ時間が設定されている）
        assertThat(jdbcTemplate.queryForObject("SELECT max(max_inactive_interval) FROM spring_session", Integer.class))
                .isEqualTo(1800);
    }

    /** 【何のため】アカウントを登録して、返ってきたセッションの Cookie を返す。 */
    private Cookie signup(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("realName", "山田 太郎", "nickname", "たろう",
                        "email", email, "password", "campfire2026", "agreedToPolicies", true))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("JSESSIONID");
    }

    /**
     * 【何のため】本番の設定（データベースに保存したセッション）でも、パスワードを変えると、
     * ほかの端末のセッションは使えなくなり、操作した端末は新しいセッション（新しい Cookie）で使い続けられることを確かめる。
     * セッションを作った日時は、データベース（spring_session.creation_time）に保存されたものと比べられる。
     */
    @Test
    void パスワードを変更するとほかのセッションは使えなくなる() throws Exception {
        String email = "prod-pw-" + UUID.randomUUID() + "@example.com";
        Cookie first = signup(email);
        // ほかの端末でもログインする
        Cookie other = mockMvc.perform(post("/api/auth/login").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("email", email, "password", "campfire2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("JSESSIONID");

        // 最初の端末で、パスワードを変える。新しいセッションの Cookie が返る
        Cookie renewed = mockMvc.perform(put("/api/me/password").cookie(first).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(
                        Map.of("currentPassword", "campfire2026", "newPassword", "new-campfire-2026"))))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("JSESSIONID");
        assertThat(renewed).isNotNull();
        assertThat(renewed.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(get("/api/me").cookie(other)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").cookie(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").cookie(renewed)).andExpect(status().isOk());
    }

    /**
     * 【何のため】本番の設定では、メールを送る仕組みがないので、パスワードの再設定を止めている（503）ことを確かめる。
     */
    @Test
    void 本番の設定では再設定を止めている() throws Exception {
        mockMvc.perform(get("/api/auth/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordResetEnabled").value(false));
        mockMvc.perform(post("/api/auth/password-reset-requests").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"taro@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"));
    }

    /**
     * 【何のため】HTTPS の通信には、HSTS のヘッダー（このサイトは HTTPS だけで開く、とブラウザに覚えさせる）が付くことを確かめる。
     * 本番では、ロードバランサーが HTTPS を受け、server.forward-headers-strategy で、HTTPS で来たことを正しく扱う。
     */
    @Test
    void HTTPSの通信にはHSTSのヘッダーが付く() throws Exception {
        mockMvc.perform(get("/api/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", org.hamcrest.Matchers.containsString("max-age=")));
    }
}
