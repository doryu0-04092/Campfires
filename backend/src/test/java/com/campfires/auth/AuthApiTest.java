package com.campfires.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.campfires.CsrfTestSupport.csrfToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.campfires.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/**
 * 【役割】アカウントの登録、ログイン、ログアウトの API のテスト。
 * 【なぜ必要か】API 仕様書（docs/api/openapi.yaml の auth）と、ログインの試行制限（非機能要件 3章）のとおりに
 * 動くことを確かめるため。
 *
 * csrfToken() は、テストの中で正しい CSRF トークンを付けてくれる道具（CsrfTestSupport）。
 * テストどうしで同じデータベースを使うので、メールアドレスは毎回ちがうもの（UUID でランダムに作る）にする。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    // ---------- テストで使う、短く書くためのメソッド ----------

    /** 【何のため】毎回ちがうメールアドレスを作る。 */
    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** 【何のため】データを JSON の文字に変える。 */
    private String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    /** 【何のため】登録の API を呼ぶ。 */
    private ResultActions signup(String email, String password, MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").with(csrfToken()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("realName", "  山田 太郎 ", "nickname", "たろう", "email", email,
                        "password", password, "agreedToPolicies", true))));
    }

    /** 【何のため】ログインの API を呼ぶ。 */
    private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrfToken()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    // ---------- 登録 ----------

    /**
     * 【何のため】登録すると、201 で自分の情報が返り、そのままログインした状態になることを確かめる。
     * パスワードはそのままではなく、変換した値で保存され、同意した規約のバージョンも記録されること。
     */
    @Test
    void 登録するとログインした状態になる() throws Exception {
        String email = newEmail();
        MockHttpSession session = new MockHttpSession();
        signup(email, "campfire2026", session)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.realName").value("山田 太郎"))
                .andExpect(jsonPath("$.nickname").value("たろう"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.createdAt").isString());

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT password_hash, policies_version, policies_agreed_at FROM users WHERE email = ?", email);
        assertThat(row.get("password_hash")).isNotEqualTo("campfire2026");
        assertThat(row.get("policies_version")).isEqualTo(AuthService.POLICIES_VERSION);
        assertThat(row.get("policies_agreed_at")).isNotNull();
    }

    /**
     * 【何のため】入力が正しくないとき、項目ごとに1つずつエラーが返ることを確かめる。
     */
    @Test
    void 登録の入力が正しくなければ400() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("realName", " ", "nickname", "あ".repeat(31), "email", "abc",
                        "password", "パスワード", "agreedToPolicies", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[?(@.field == 'realName')].message").value("本名を入力してください。"))
                .andExpect(jsonPath("$.errors[?(@.field == 'nickname')].message").value("ニックネームは30文字以内にしてください。"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("メールアドレスの形が正しくありません。"))
                .andExpect(jsonPath("$.errors[?(@.field == 'agreedToPolicies')]").exists());
    }

    /**
     * 【何のため】登録済みのメールアドレスで登録すると 409 になることを確かめる（大文字と小文字は区別しない）。
     */
    @Test
    void 同じメールアドレスでは登録できない() throws Exception {
        String email = newEmail();
        signup(email, "campfire2026", new MockHttpSession()).andExpect(status().isCreated());
        signup(email.toUpperCase(), "campfire2026", new MockHttpSession())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    // ---------- ログイン ----------

    /**
     * 【何のため】ログインに成功すると、自分の情報が返り、セッションの番号が作り直されることを確かめる。
     */
    @Test
    void ログインするとセッションの番号が作り直される() throws Exception {
        String email = newEmail();
        signup(email, "campfire2026", new MockHttpSession()).andExpect(status().isCreated());

        MockHttpSession session = new MockHttpSession();
        String before = session.getId();
        login(email.toUpperCase(), "campfire2026", session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        assertThat(session.getId()).isNotEqualTo(before);

        mockMvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
    }

    /**
     * 【何のため】パスワードが違うときも、メールアドレスが登録されていないときも、同じ 401 になることを確かめる。
     */
    @Test
    void メールアドレスかパスワードが違えば401() throws Exception {
        String email = newEmail();
        signup(email, "campfire2026", new MockHttpSession()).andExpect(status().isCreated());

        login(email, "wrong-password", new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(newEmail(), "campfire2026", new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    /**
     * 【何のため】空の項目があれば 400 になることを確かめる。
     */
    @Test
    void ログインの入力が空なら400() throws Exception {
        login(" ", "", new MockHttpSession())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    /**
     * 【何のため】5回続けて失敗するとロックされ、正しいパスワードでも 429 になること、
     * ロックが解けたらログインできることを確かめる。
     */
    @Test
    void 続けて5回失敗するとロックされる() throws Exception {
        String email = newEmail();
        signup(email, "campfire2026", new MockHttpSession()).andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password", new MockHttpSession()).andExpect(status().isUnauthorized());
        }
        login(email, "campfire2026", new MockHttpSession())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(header().exists("Retry-After"));
        String retryAfter = login(email, "campfire2026", new MockHttpSession())
                .andReturn().getResponse().getHeader("Retry-After");
        // 15分 = 900秒以内
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 900L);

        // 15分たったことにする（ロックが解ける日時を、過去にずらす）
        jdbcTemplate.update("UPDATE login_failures SET locked_until = now() - interval '1 second' WHERE email = ?", email);
        login(email, "campfire2026", new MockHttpSession()).andExpect(status().isOk());
        // 成功したら、失敗の記録が消える
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM login_failures WHERE email = ?", Integer.class, email))
                .isZero();
    }

    // ---------- ログアウト ----------

    /**
     * 【何のため】ログアウトすると 204 になり、その後はログインしていない状態（401）になることを確かめる。
     */
    @Test
    void ログアウトするとログインしていない状態になる() throws Exception {
        MockHttpSession session = new MockHttpSession();
        signup(newEmail(), "campfire2026", session).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/logout").with(csrfToken()).session(session))
                .andExpect(status().isNoContent());
        // サーバーのセッションが消されている
        assertThat(session.isInvalid()).isTrue();
    }
}
