package com.campfires.account;

import static com.campfires.CsrfTestSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.campfires.ApiTestBase;

/**
 * 【役割】パスワードの変更の API（PUT /api/me/password、F-24）のテスト。
 * 【なぜ必要か】要件定義書 5.13 のルールのとおりに動くことを確かめるため。特に大事なのは、次の2つ。
 * - ほかの端末のログイン（セッション）は使えなくなる（パスワードを盗まれていた場合に、追い出せるように）
 * - 操作した端末は、新しいセッションで、ログインしたまま使い続けられる（セッションの番号も新しくなる）
 */
class PasswordChangeApiTest extends ApiTestBase {

    // 新しいパスワード（開発用の偽の値）
    private static final String NEW_PASSWORD = "new-campfire-2026";

    /** 【何のため】ログインしたユーザーとして、パスワードの変更の API を呼ぶ。 */
    private ResultActions changePassword(MockHttpSession session, String currentPassword, String newPassword)
            throws Exception {
        return mockMvc.perform(withJson(put("/api/me/password"),
                Map.of("currentPassword", currentPassword, "newPassword", newPassword)).session(session));
    }

    /** 【何のため】ログインの API を呼ぶ。 */
    private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", password))
                .session(session));
    }

    /**
     * 【何のため】パスワードを変えると 204 になり、操作した端末は「新しいセッション」でログインしたままになることを確かめる。
     * 古いセッションは捨てられ（セッション固定攻撃の対策）、新しいパスワードでだけログインできる。
     */
    @Test
    void パスワードを変更すると新しいセッションでログインしたままになる() throws Exception {
        Account me = newAccount();
        String oldSessionId = me.session().getId();

        MvcResult result = changePassword(me.session(), PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn();

        // 古いセッションは捨てられ、新しいセッション（番号も違う）が作られている
        assertThat(me.session().isInvalid()).isTrue();
        MockHttpSession newSession = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(newSession).isNotNull();
        assertThat(newSession.getId()).isNotEqualTo(oldSessionId);
        // 新しいセッションは「変えた日時」より後に作られているので、使い続けられる
        Timestamp changedAt = jdbcTemplate.queryForObject("SELECT password_changed_at FROM users WHERE id = ?",
                Timestamp.class, me.id());
        assertThat(newSession.getCreationTime()).isGreaterThanOrEqualTo(changedAt.getTime());
        mockMvc.perform(get("/api/me").session(newSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.id()));

        // 新しいパスワードでログインでき、古いパスワードではログインできない
        login(me.email(), NEW_PASSWORD, new MockHttpSession()).andExpect(status().isOk());
        login(me.email(), PASSWORD, new MockHttpSession()).andExpect(status().isUnauthorized());
        // 保存しているのは BCrypt で変換した値（新しいパスワードそのものではない）
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, me.id());
        assertThat(hash).startsWith("$2").doesNotContain(NEW_PASSWORD);
    }

    /**
     * 【何のため】パスワードを変えると、ほかの端末（別のセッション）のログインは使えなくなる（401）ことを確かめる。
     * 止めたセッションの Cookie は消され、ログインなしで見られる API は、ログインしていない人として見られる。
     */
    @Test
    void ほかの端末のログインは使えなくなる() throws Exception {
        Account me = newAccount();
        // ほかの端末（スマートフォンなど）でもログインしておく
        MockHttpSession otherDevice = new MockHttpSession();
        login(me.email(), PASSWORD, otherDevice).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").session(otherDevice)).andExpect(status().isOk());

        // 最初の端末で、パスワードを変える
        MvcResult result = changePassword(me.session(), PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn();

        // ほかの端末のセッションは、使えなくなる
        mockMvc.perform(get("/api/me").session(otherDevice))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(otherDevice.isInvalid()).isTrue();
        // ログインなしで見られる API は、そのまま見られる
        mockMvc.perform(get("/api/events")).andExpect(status().isOk());

        // 操作した端末（新しいセッション）は、使い続けられる
        MockHttpSession newSession = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/me").session(newSession)).andExpect(status().isOk());
        // パスワードを変えた後に、新しいパスワードでログインし直せば、ほかの端末でも使える
        MockHttpSession relogin = new MockHttpSession();
        login(me.email(), NEW_PASSWORD, relogin).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").session(relogin)).andExpect(status().isOk());
    }

    /**
     * 【何のため】今のパスワードが違えば、currentPassword の項目のエラー（400、INVALID_PASSWORD）になり、
     * パスワードは変わらず、ログインも続くことを確かめる。
     */
    @Test
    void 今のパスワードが違えば変更できない() throws Exception {
        Account me = newAccount();
        changePassword(me.session(), "wrong-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

        mockMvc.perform(get("/api/me").session(me.session())).andExpect(status().isOk());
        login(me.email(), PASSWORD, new MockHttpSession()).andExpect(status().isOk());
    }

    /**
     * 【何のため】新しいパスワードが、アカウントの登録と同じルール（8〜64文字、半角の英字・数字・記号だけ）に
     * 合わなければ、newPassword の項目のエラー（VALIDATION_ERROR）になることを確かめる。
     */
    @Test
    void 新しいパスワードがルールに合わなければ変更できない() throws Exception {
        Account me = newAccount();
        for (String invalid : new String[] { "short1", "a".repeat(65), "パスワードです12345" }) {
            changePassword(me.session(), PASSWORD, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        }
        // 今のパスワードが空なら、currentPassword の項目のエラー
        changePassword(me.session(), "", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
    }

    /**
     * 【何のため】今と同じパスワードには変えられない（newPassword の項目のエラー）ことを確かめる。
     */
    @Test
    void 今と同じパスワードには変更できない() throws Exception {
        Account me = newAccount();
        changePassword(me.session(), PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        assertThat(jdbcTemplate.queryForObject("SELECT password_changed_at FROM users WHERE id = ?", Timestamp.class,
                me.id())).isNull();
    }

    /**
     * 【何のため】ログインしていなければ 401、CSRF トークンがなければ 403 になることを確かめる。
     */
    @Test
    void ログインとCSRFトークンが必要() throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD));
        mockMvc.perform(put("/api/me/password").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        Account me = newAccount();
        mockMvc.perform(put("/api/me/password").session(me.session()).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        // パスワードは変わっていない
        login(me.email(), PASSWORD, new MockHttpSession()).andExpect(status().isOk());
    }
}
