package com.campfires.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.campfires.ApiTestBase;

/**
 * 【役割】パスワードの再設定を止めている（campfires.password-reset.enabled=false）ときのテスト。
 * 【なぜ必要か】本番には、メールを送る仕組みがまだないので、再設定を止めている（application-prod.properties）。
 * 止めているときは、どちらの API も 503（PASSWORD_RESET_UNAVAILABLE）を返し、トークンを作らないこと、
 * 画面がリンクを隠せるように、使える機能の一覧で false を返すことを確かめる。
 * @TestPropertySource で、このテストだけ設定を変えて起動する（ほかのテストとは別の Spring の起動になる）。
 */
@TestPropertySource(properties = "campfires.password-reset.enabled=false")
class PasswordResetDisabledApiTest extends ApiTestBase {

    /**
     * 【何のため】止めているときは、使える機能の一覧で passwordResetEnabled が false になることを確かめる。
     */
    @Test
    void 使える機能の一覧で止めていると分かる() throws Exception {
        mockMvc.perform(get("/api/auth/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordResetEnabled").value(false));
    }

    /**
     * 【何のため】止めているときは、登録されているメールアドレスでも、依頼は 503 になり、トークンは作られないことを確かめる。
     * 問い合わせを案内する文を返す。
     */
    @Test
    void 止めているときは依頼が503() throws Exception {
        Account me = newAccount();
        mockMvc.perform(withJson(post("/api/auth/password-reset-requests"), Map.of("email", me.email())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("お問い合わせ")));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE user_id = ?",
                Integer.class, me.id())).isZero();
    }

    /**
     * 【何のため】止めているときは、再設定も 503 になることを確かめる。
     */
    @Test
    void 止めているときは再設定が503() throws Exception {
        mockMvc.perform(withJson(post("/api/auth/password-resets"),
                Map.of("token", "any-token", "newPassword", "new-campfire-2026")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"));
    }
}
