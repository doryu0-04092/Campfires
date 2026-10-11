package com.campfires.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.campfires.ApiTestBase;

/**
 * 【役割】とても長いメールアドレスでログインを試したときの動きのテスト。
 * 【なぜ必要か】ログインの失敗は、メールアドレスごとに login_failures テーブルに記録する。
 * このテーブルのメールアドレスの列は 254 文字まで。ログインの入力（LoginRequest）には文字数のルールがないので、
 * 255 文字以上のメールアドレスで試されたときも、ふつうの失敗（401）として扱えるかを確かめる。
 */
class LoginLongEmailTest extends ApiTestBase {

    /**
     * 【何のため】254文字ちょうどのメールアドレスなら、ふつうの失敗（401）になることを確かめる（列にちょうど入る）。
     */
    @Test
    void 長さ254のメールアドレスはふつうの失敗() throws Exception {
        String email = "a".repeat(242) + "@example.com";

        mockMvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    /**
     * 【何のため】255文字のメールアドレスでも、ふつうの失敗（401）になることを確かめる。
     * 以前は、失敗の記録（login_failures.email は 254 文字まで）に入りきらず、データベースのエラーで 500 になっていた。
     * 254 文字を超えるメールアドレスは登録できない（＝ログインできるユーザーはいない）ので、失敗の記録をせずに 401 を返すように直した。
     */
    @Test
    void 長さ255のメールアドレスもふつうの失敗() throws Exception {
        String email = "a".repeat(243) + "@example.com";

        mockMvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
