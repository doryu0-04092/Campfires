package com.campfires.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.campfires.TestcontainersConfiguration;

/**
 * 【役割】セキュリティの基本設定（SecurityConfig）と、エラーの返し方を確かめるテスト。
 * 【なぜ必要か】ログインしていないときの 401、CSRF トークンがないときの 403 が、
 * API 仕様書（docs/api/README.md 1.4〜1.7）のとおりの形で返ることを確かめるため。
 *
 * MockMvc は、本物のサーバーを立てずに、API を呼んだのと同じことをテストの中で行える道具。
 * @AutoConfigureMockMvc で、MockMvc を使えるようにする。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 【何のため】CSRF トークンの API が、XSRF-TOKEN の Cookie を返すことを確かめる。
     */
    @Test
    void CSRFトークンのAPIはCookieで合言葉を返す() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    /**
     * 【何のため】ログインが必要な API を、ログインせずに呼ぶと、401 と決まった形のエラーが返ることを確かめる。
     */
    @Test
    void ログインせずにログインが必要なAPIを呼ぶと401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /**
     * 【何のため】データを変える API を、CSRF トークンなしで呼ぶと、403 になることを確かめる。
     */
    @Test
    void CSRFトークンなしでデータを変えるAPIを呼ぶと403() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    /**
     * 【何のため】存在しない URL を呼ぶと、404 と決まった形のエラーが返ることを確かめる。
     * /api/communities/* は、ログインなしで呼べる URL なので、ログインのチェックではなく 404 になる。
     */
    @Test
    void 存在しないURLは404() throws Exception {
        mockMvc.perform(get("/api/communities/1/unknown/path"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/communities/1/events/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
