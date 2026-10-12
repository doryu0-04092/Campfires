package com.campfires.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.campfires.ApiTestBase;
import com.campfires.account.User;

/**
 * 【役割】パスワードの再設定の API（POST /api/auth/password-reset-requests、POST /api/auth/password-resets、
 * GET /api/auth/features。F-25）のテスト。
 * 【なぜ必要か】要件定義書 5.13 のルール（登録の有無を教えない、トークンは変換した値だけを保存する、30分で期限切れ、
 * 1回だけ使える、ほかのログインを止める、ロックを解く、自動ではログインしない）のとおりに動くことを確かめるため。
 *
 * メールを送る仕組みはないので、「届ける係」（PasswordResetNotifier）を、届いたリンクを覚えておくだけの
 * テスト用の係（RecordingNotifier）に差し替えて、リンクに入っているトークンを取り出して使う。
 * 差し替えるので、ほかの API のテストとは別の Spring の起動になる。
 */
class PasswordResetApiTest extends ApiTestBase {

    private static final String NEW_PASSWORD = "reset-campfire-2026";

    /**
     * 【何のため】届いたリンクを、ユーザーの番号ごとに覚えておく、テスト用の「届ける係」。
     */
    static class RecordingNotifier implements PasswordResetNotifier {
        // ユーザーの番号 → 最後に届いたリンク。ConcurrentHashMap は、同時に読み書きしても壊れない入れ物
        final Map<Long, String> lastUrls = new ConcurrentHashMap<>();

        @Override
        public void send(User user, String resetUrl) {
            lastUrls.put(user.id(), resetUrl);
        }
    }

    /**
     * 【何のため】テストのときだけ、届ける係を RecordingNotifier に差し替える設定。
     * テストのクラスの中に書いた @TestConfiguration は、Spring が自動で読み込む。
     * @Primary は「同じ種類の部品が2つあるときは、こちらを使う」という印（NoopPasswordResetNotifier より優先される）。
     */
    @TestConfiguration
    static class NotifierConfig {
        @Bean
        @Primary
        RecordingNotifier recordingNotifier() {
            return new RecordingNotifier();
        }
    }

    @Autowired
    private RecordingNotifier notifier;

    // ---------- テストで使う、短く書くためのメソッド ----------

    /** 【何のため】再設定の依頼の API を呼ぶ。 */
    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/password-reset-requests"), Map.of("email", email)));
    }

    /** 【何のため】再設定の API を呼ぶ。 */
    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/password-resets"),
                Map.of("token", token, "newPassword", newPassword)));
    }

    /** 【何のため】ログインの API を呼ぶ。 */
    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", password))
                .session(new MockHttpSession()));
    }

    /** 【何のため】そのユーザーに最後に届いたリンクから、トークンを取り出す。 */
    private String lastToken(long userId) {
        String url = notifier.lastUrls.get(userId);
        assertThat(url).as("リンクが届いている").isNotNull();
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    /** 【何のため】再設定を依頼して、届いたリンクのトークンを返す。 */
    private String requestAndGetToken(Account account) throws Exception {
        requestReset(account.email()).andExpect(status().isAccepted());
        return lastToken(account.id());
    }

    // ---------- 使える機能の一覧 ----------

    /**
     * 【何のため】開発・自動テストの設定では、再設定を使える（passwordResetEnabled が true）ことを、ログインなしで確かめられる。
     */
    @Test
    void 使える機能の一覧で再設定を使えると分かる() throws Exception {
        mockMvc.perform(get("/api/auth/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordResetEnabled").value(true));
    }

    // ---------- 再設定の依頼 ----------

    /**
     * 【何のため】登録されていても、いなくても、同じ返事（202、同じ中身）になることを確かめる（メールアドレスを探られないように）。
     * 登録されているときだけ、リンクが届く。
     */
    @Test
    void 登録の有無にかかわらず同じ返事になる() throws Exception {
        Account me = newAccount();
        String registered = requestReset(me.email())
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String unknown = requestReset("nobody-" + UUID.randomUUID() + "@example.com")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(registered).contains("message");
        assertThat(notifier.lastUrls).containsKey(me.id());
    }

    /**
     * 【何のため】メールアドレスの大文字・小文字と前後の空白は、区別せずに扱うことを確かめる（ログインと同じ）。
     */
    @Test
    void メールアドレスの大文字と小文字は区別しない() throws Exception {
        Account me = newAccount();
        requestReset("  " + me.email().toUpperCase() + " ").andExpect(status().isAccepted());
        assertThat(notifier.lastUrls).containsKey(me.id());
    }

    /**
     * 【何のため】データベースには、トークンそのものではなく SHA-256 で変換した値だけを保存し、
     * 有効期限は作ってから30分であることを確かめる。リンクは、設定した画面の入り口の URL から始まる。
     */
    @Test
    void トークンは変換した値だけを30分の期限で保存する() throws Exception {
        Account me = newAccount();
        String token = requestAndGetToken(me);

        assertThat(notifier.lastUrls.get(me.id())).isEqualTo("http://localhost:5173/password/reset#token=" + token);
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT token_hash, (expires_at - created_at = interval '30 minutes') AS thirty_minutes, used_at
                FROM password_reset_tokens WHERE user_id = ?
                """, me.id());
        assertThat(row.get("token_hash")).isEqualTo(PasswordResetService.sha256Hex(token));
        assertThat(row.get("thirty_minutes")).isEqualTo(true);
        assertThat(row.get("used_at")).isNull();
        // トークンそのものは、どこにも保存されていない
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class, token)).isZero();
    }

    /**
     * 【何のため】もう一度依頼すると、前のリンクは使えなくなり、新しいリンクだけが使えることを確かめる。
     */
    @Test
    void もう一度依頼すると前のリンクは使えなくなる() throws Exception {
        Account me = newAccount();
        String first = requestAndGetToken(me);
        String second = requestAndGetToken(me);
        assertThat(second).isNotEqualTo(first);

        resetPassword(first, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        resetPassword(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    /**
     * 【何のため】メールアドレスの形が正しくなければ、400（VALIDATION_ERROR）になることを確かめる
     * （登録されているかどうかに関係ないので、探る手がかりにはならない）。
     */
    @Test
    void メールアドレスの形が正しくなければ400() throws Exception {
        requestReset("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    /**
     * 【何のため】ログインしていなくても呼べるが、CSRF トークンは必要（403）であることを確かめる。
     */
    @Test
    void CSRFトークンが必要() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"taro@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(post("/api/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"x\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- 再設定 ----------

    /**
     * 【何のため】リンクのトークンで再設定すると 204 になり、新しいパスワードでだけログインできることを確かめる。
     * 自動ではログインしない（再設定の返事のセッションは、ログインしていない）。
     */
    @Test
    void 再設定すると新しいパスワードでログインできる() throws Exception {
        Account me = newAccount();
        String token = requestAndGetToken(me);

        MockHttpSession resetSession = new MockHttpSession();
        mockMvc.perform(withJson(post("/api/auth/password-resets"), Map.of("token", token, "newPassword", NEW_PASSWORD))
                .session(resetSession))
                .andExpect(status().isNoContent());
        // 自動ではログインしていない
        mockMvc.perform(get("/api/me").session(resetSession)).andExpect(status().isUnauthorized());

        login(me.email(), NEW_PASSWORD).andExpect(status().isOk());
        login(me.email(), PASSWORD).andExpect(status().isUnauthorized());
        // トークンは使用済みになっている
        assertThat(jdbcTemplate.queryForObject(
                "SELECT used_at IS NOT NULL FROM password_reset_tokens WHERE user_id = ?", Boolean.class, me.id()))
                .isTrue();
    }

    /**
     * 【何のため】同じリンクは1回だけ使える（2回目は 400、INVALID_RESET_TOKEN）ことを確かめる。
     */
    @Test
    void 同じリンクは1回だけ使える() throws Exception {
        Account me = newAccount();
        String token = requestAndGetToken(me);
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        resetPassword(token, "another-password-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        login(me.email(), NEW_PASSWORD).andExpect(status().isOk());
    }

    /**
     * 【何のため】有効期限（30分）を過ぎたリンクは使えないことを確かめる。期限は、データベースの値を過去に書き換えて作る。
     */
    @Test
    void 期限切れのリンクは使えない() throws Exception {
        Account me = newAccount();
        String token = requestAndGetToken(me);
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?",
                me.id());

        resetPassword(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        login(me.email(), PASSWORD).andExpect(status().isOk());
    }

    /**
     * 【何のため】知らないトークンは、期限切れ・使用済みと同じ返事（400、INVALID_RESET_TOKEN）になることを確かめる。
     */
    @Test
    void 知らないトークンは使えない() throws Exception {
        resetPassword("unknown-token-" + UUID.randomUUID(), NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    /**
     * 【何のため】新しいパスワードがルールに合わなければ 400（VALIDATION_ERROR）で、トークンは使われずに残ることを確かめる。
     */
    @Test
    void 新しいパスワードがルールに合わなければ再設定できない() throws Exception {
        Account me = newAccount();
        String token = requestAndGetToken(me);

        resetPassword(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        // トークンは、まだ使える
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    /**
     * 【何のため】再設定すると、それまでのログイン（ほかの端末のセッション）は、すべて使えなくなることを確かめる。
     */
    @Test
    void 再設定するとそれまでのログインは使えなくなる() throws Exception {
        Account me = newAccount();
        mockMvc.perform(get("/api/me").session(me.session())).andExpect(status().isOk());

        resetPassword(requestAndGetToken(me), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").session(me.session())).andExpect(status().isUnauthorized());
    }

    /**
     * 【何のため】ログインに続けて失敗してロックされていても、再設定すると、ロックが解けてすぐにログインできることを確かめる。
     */
    @Test
    void 再設定するとログインのロックが解ける() throws Exception {
        Account me = newAccount();
        for (int i = 0; i < AuthService.MAX_LOGIN_FAILURES; i++) {
            login(me.email(), "wrong-password").andExpect(status().isUnauthorized());
        }
        login(me.email(), PASSWORD).andExpect(status().isTooManyRequests());

        resetPassword(requestAndGetToken(me), NEW_PASSWORD).andExpect(status().isNoContent());

        login(me.email(), NEW_PASSWORD).andExpect(status().isOk());
    }

    /**
     * 【何のため】パスワードを変更（F-24）すると、まだ使っていない再設定のリンクは使えなくなることを確かめる。
     */
    @Test
    void パスワードを変更すると再設定のリンクは使えなくなる() throws Exception {
        Account me = newAccount();
        String token = requestAndGetToken(me);

        mockMvc.perform(as(me, put("/api/me/password"),
                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        resetPassword(token, "another-password-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    /**
     * 【何のため】アカウントを削除すると、そのアカウントの再設定のトークンも消え、リンクは使えなくなることを確かめる。
     * 削除したアカウントのメールアドレスで依頼しても、リンクは届かない。
     */
    @Test
    void アカウントを削除するとリンクは消える() throws Exception {
        Account me = newAccount();
        String token = requestAndGetToken(me);

        mockMvc.perform(as(me, delete("/api/me"), Map.of("password", PASSWORD))).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE user_id = ?",
                Integer.class, me.id())).isZero();
        resetPassword(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        notifier.lastUrls.remove(me.id());
        requestReset(me.email()).andExpect(status().isAccepted());
        assertThat(notifier.lastUrls).doesNotContainKey(me.id());
    }

    /**
     * 【何のため】トークンが空なら、探す前に、token の項目のエラー（VALIDATION_ERROR）になることを確かめる。
     */
    @Test
    void トークンが空なら400() throws Exception {
        mockMvc.perform(withJson(post("/api/auth/password-resets"), Map.of("token", "", "newPassword", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("token"));
    }
}
