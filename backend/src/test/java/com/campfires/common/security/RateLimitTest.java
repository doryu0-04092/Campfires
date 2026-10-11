package com.campfires.common.security;

import static com.campfires.CsrfTestSupport.csrfToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.campfires.TestcontainersConfiguration;

/**
 * 【役割】ログインの回数制限（IP アドレスごと）のテスト（Issue #47）。
 * 【なぜ必要か】同じ IP アドレスから、決まった回数（10分間に 20回）を超えてログインを試すと 429 になり、
 * ほかの IP アドレスは影響を受けないことを確かめるため。
 * 回数制限は本番だけ使うので、このテストだけ、properties で使うようにして起動する。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "campfires.rate-limit.enabled=true")
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    /** 【何のため】指定した IP アドレスから、ログインを試すリクエストを作る。 */
    private MockHttpServletRequestBuilder login(String ip) {
        return post("/api/auth/login").with(csrfToken()).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + UUID.randomUUID() + "@example.com\",\"password\":\"wrong-password\"}");
    }

    /**
     * 【何のため】同じ IP アドレスから 21回目のログインは 429 になり、ほかの IP アドレスはログインを試せることを確かめる。
     */
    @Test
    void 同じIPアドレスから試しすぎると429() throws Exception {
        for (int i = 0; i < RateLimitFilter.LOGIN.maxRequests(); i++) {
            mockMvc.perform(login("203.0.113.10")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("203.0.113.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(login("203.0.113.20")).andExpect(status().isUnauthorized());
    }
}
