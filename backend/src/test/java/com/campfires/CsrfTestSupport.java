package com.campfires;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

/**
 * 【役割】テストの API の呼び出しに、CSRF トークン（合言葉）を付ける道具。
 * 【なぜ必要か】データを変える API は、XSRF-TOKEN の Cookie と、同じ値の X-XSRF-TOKEN ヘッダーがないと 403 になる。
 * 本物の画面と同じように、Cookie とヘッダーの両方に、同じ合言葉を入れて送る（docs/api/README.md 1.7）。
 *
 * Spring Security のテスト用の csrf() は、テスト全体で共有している設定（合言葉の保存先）を書き換えてしまい、
 * ほかのテスト（Cookie で合言葉が届くかのテスト）が失敗するので、使わない。
 */
public final class CsrfTestSupport {

    private CsrfTestSupport() {
    }

    /**
     * 【何のため】API の呼び出しに、CSRF トークンを付ける。使い方：mockMvc.perform(post("…").with(csrfToken()))
     * RequestPostProcessor は「送る前のリクエストに手を加える」道具。
     */
    public static RequestPostProcessor csrfToken() {
        return request -> {
            // 推測できないランダムな合言葉を作る
            String token = UUID.randomUUID().toString();
            // すでに付いている Cookie（あれば）に、XSRF-TOKEN の Cookie を足す
            List<Cookie> cookies = new ArrayList<>();
            if (request.getCookies() != null) {
                cookies.addAll(List.of(request.getCookies()));
            }
            cookies.add(new Cookie("XSRF-TOKEN", token));
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}
