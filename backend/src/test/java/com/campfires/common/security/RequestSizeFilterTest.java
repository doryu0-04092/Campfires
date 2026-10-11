package com.campfires.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 【役割】1回の通信で送れるデータの大きさを制限する RequestSizeFilter の単体テスト。
 * 【なぜ必要か】上限ちょうど（通す）と、上限 + 1 バイト（断る）の境目や、大きさの分からない送り方（chunked）を、
 * Spring を起動せずに確かめるため。API のテスト（ProductionSettingsTest）では、大きすぎる場合しか確かめていない。
 *
 * MockHttpServletRequest などは、Spring が用意しているテスト用の偽物のリクエスト・返事。
 * MockFilterChain は「フィルターを通り抜けた先」の偽物で、通り抜けたかどうかを後から調べられる。
 */
class RequestSizeFilterTest {

    // テストでは、上限を小さく（100 バイト）して確かめる
    private static final long MAX = 100;

    private final RequestSizeFilter filter =
            new RequestSizeFilter(MAX, new ProblemResponseWriter(ProblemResponseWriterTest.jsonMapper()));

    /** 【何のため】指定した大きさの中身を付けた、POST のリクエストを作る。 */
    private static MockHttpServletRequest post(int bytes) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/communities");
        request.setContent(new byte[bytes]);
        return request;
    }

    /** 【何のため】フィルターを通し、通り抜けたかどうかを返す。 */
    private boolean passes(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // 通り抜けたときだけ、chain にリクエストが記録される
        return chain.getRequest() != null;
    }

    /**
     * 【何のため】0 バイト、上限ちょうどは通すことを確かめる。
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 99, 100 })
    void 上限以下なら通す(int bytes) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(passes(post(bytes), response)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * 【何のため】上限より1バイトでも大きければ、中身を読む前に 413（PAYLOAD_TOO_LARGE）で断ることを確かめる。
     */
    @Test
    void 上限より1バイト大きければ413() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(passes(post(101), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }

    /**
     * 【何のため】中身のない GET（大きさが分からない＝-1）は、そのまま通すことを確かめる。
     */
    @Test
    void 中身のないリクエストは通す() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");

        assertThat(request.getContentLengthLong()).isEqualTo(-1);
        assertThat(passes(request, new MockHttpServletResponse())).isTrue();
    }

    /**
     * 【何のため】大きさの分からない送り方（Transfer-Encoding: chunked）は、大文字・小文字や、ほかの値と並べた書き方でも、
     * 411（LENGTH_REQUIRED）で断ることを確かめる。
     */
    @ParameterizedTest
    @ValueSource(strings = { "chunked", "CHUNKED", "gzip, chunked" })
    void chunkedの送り方は411(String transferEncoding) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/communities");
        request.addHeader("Transfer-Encoding", transferEncoding);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(passes(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(411);
        assertThat(response.getContentAsString()).contains("LENGTH_REQUIRED");
    }

    /**
     * 【何のため】chunked 以外の Transfer-Encoding（gzip だけ）は、断らないことを確かめる。
     */
    @Test
    void chunked以外の送り方は通す() throws Exception {
        MockHttpServletRequest request = post(10);
        request.addHeader("Transfer-Encoding", "gzip");

        assertThat(passes(request, new MockHttpServletResponse())).isTrue();
    }
}
