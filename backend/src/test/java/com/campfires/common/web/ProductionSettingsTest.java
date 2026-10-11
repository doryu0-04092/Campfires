package com.campfires.common.web;

import static com.campfires.CsrfTestSupport.csrfToken;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.campfires.ApiTestBase;

/**
 * 【役割】本番で必要な設定（画面のファイルを返す、死活監視、データの大きさの上限、HTTP ヘッダー）のテスト（Issue #47）。
 * 【なぜ必要か】本番では、画面と API を1つの箱にまとめて返す。画面の URL を直接開いても画面が出ること、
 * API のエラーは今までどおり JSON で返ること、セキュリティの設定が効いていることを確かめるため。
 * 画面のファイルは、テスト用の src/test/resources/static/index.html を使う。
 */
class ProductionSettingsTest extends ApiTestBase {

    /**
     * 【何のため】画面の URL（ファイルがない URL）を開くと、画面の入り口（index.html）が返ることを確かめる。
     * ログインしていなくても、画面のファイルは読めること。
     */
    @Test
    void 画面のURLを直接開くと画面の入り口が返る() throws Exception {
        // 「/」は、Spring Boot が index.html へ内側で転送する（forward）。テストの道具は転送の先を開かないので、転送先を確かめる
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        for (String url : new String[] { "/communities/1", "/manage/select/event-edit" }) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<div id=\"root\">")));
        }
        // 拡張子の付いた、ないファイルは 404（画面を返さない）
        mockMvc.perform(get("/assets/not-found.js")).andExpect(status().isNotFound());
        // ない API は、画面ではなく、JSON のエラーを返す（ログインが必要な API なので、まず 401）
        mockMvc.perform(get("/api/not-found"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * 【何のため】死活監視の API が、ログインしていなくても 200 を返すことを確かめる。
     */
    @Test
    void 死活監視のAPIは200を返す() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    /**
     * 【何のため】画面を守る HTTP ヘッダーが付いていることを確かめる。
     */
    @Test
    void 画面を守るHTTPヘッダーが付く() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /**
     * 【何のため】上限（64KB）を超えるデータは 413、大きさの分からない送り方は 411 で断ることを確かめる。
     */
    @Test
    void 大きすぎるデータは断る() throws Exception {
        String big = "{\"email\":\"" + "a".repeat(70_000) + "\",\"password\":\"x\"}";
        mockMvc.perform(post("/api/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content(big))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        mockMvc.perform(post("/api/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .header("Transfer-Encoding", "chunked").content("{}"))
                .andExpect(status().isLengthRequired())
                .andExpect(jsonPath("$.code").value("LENGTH_REQUIRED"));
    }
}
