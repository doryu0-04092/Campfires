package com.campfires.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 【役割】ログイン・アカウント登録の回数制限（RateLimitFilter）の単体テスト。
 * 【なぜ必要か】回数制限は「時間の区切り（窓）」で数え直すので、時刻をテストから進めないと確かめられない。
 * 時刻（Clock）を差し替えて、区切りの境目、Retry-After の秒数、数えない通信、古い回数の片付けを確かめる。
 * API のテスト（RateLimitTest）では「21回目が 429 になる」ことだけを確かめている。
 */
class RateLimitFilterTest {

    // ある区切り（ログインは600秒、アカウント登録は3600秒）のちょうど始まりの時刻（秒）。36000 は 600 でも 3600 でも割り切れる
    private static final long WINDOW_START = 36_000;

    private RateLimitFilter filter;

    /**
     * 【何のため】テストごとに、新しいフィルターを作る（回数を 0 から数える）。
     * @BeforeEach を付けたメソッドは、各テストの前に毎回動く。
     */
    @BeforeEach
    void setUp() {
        filter = newFilter(true);
    }

    private static RateLimitFilter newFilter(boolean enabled) {
        RateLimitFilter created =
                new RateLimitFilter(enabled, new ProblemResponseWriter(ProblemResponseWriterTest.jsonMapper()));
        created.setClock(clockAt(WINDOW_START));
        return created;
    }

    /** 【何のため】指定した秒の時刻で止まった時計を作る（Clock.fixed は、いつ聞いても同じ時刻を返す時計）。 */
    private static Clock clockAt(long epochSecond) {
        return Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }

    /** 【何のため】リクエストを1回送り、返事を返す。 */
    private MockHttpServletResponse send(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /** 【何のため】同じリクエストを何回も送る。 */
    private void sendTimes(int times, String uri, String ip) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(send("POST", uri, ip).getStatus()).isEqualTo(200);
        }
    }

    /**
     * 【何のため】ログインは、区切り（600秒）の中で20回までは通し、21回目は 429 で断ることを確かめる。
     */
    @Test
    void ログインは20回まで通して21回目は429() throws Exception {
        sendTimes(RateLimitFilter.LOGIN.maxRequests(), "/api/auth/login", "203.0.113.1");

        MockHttpServletResponse response = send("POST", "/api/auth/login", "203.0.113.1");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    /**
     * 【何のため】Retry-After が「次の区切りまでの秒数」になることを確かめる。
     * 区切りの始まりから100秒後に断られたら、あと500秒（600 - 100）。
     */
    @Test
    void RetryAfterは次の区切りまでの秒数() throws Exception {
        sendTimes(20, "/api/auth/login", "203.0.113.1");

        filter.setClock(clockAt(WINDOW_START + 100));
        assertThat(send("POST", "/api/auth/login", "203.0.113.1").getHeader("Retry-After")).isEqualTo("500");

        // 区切りの最後の1秒（599秒後）なら、あと1秒
        filter.setClock(clockAt(WINDOW_START + 599));
        assertThat(send("POST", "/api/auth/login", "203.0.113.1").getHeader("Retry-After")).isEqualTo("1");
    }

    /**
     * 【何のため】次の区切りになると、0 から数え直して、また通すことを確かめる。
     */
    @Test
    void 次の区切りになれば数え直す() throws Exception {
        sendTimes(20, "/api/auth/login", "203.0.113.1");
        assertThat(send("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(429);

        // ちょうど次の区切りの始まり（600秒後）
        filter.setClock(clockAt(WINDOW_START + 600));
        sendTimes(20, "/api/auth/login", "203.0.113.1");
        assertThat(send("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(429);
    }

    /**
     * 【何のため】アカウント登録は、1時間に10回まで（ログインとは別に数える）ことを確かめる。
     */
    @Test
    void アカウント登録は10回までで別に数える() throws Exception {
        sendTimes(RateLimitFilter.SIGNUP.maxRequests(), "/api/auth/signup", "203.0.113.1");

        MockHttpServletResponse response = send("POST", "/api/auth/signup", "203.0.113.1");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("3600");
        // ログインは、まだ1回も数えていないので通す
        assertThat(send("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(200);
    }

    /**
     * 【何のため】IP アドレスごとに数えることを確かめる（ほかの人の試しすぎに巻き込まれない）。
     */
    @Test
    void IPアドレスごとに数える() throws Exception {
        sendTimes(20, "/api/auth/login", "203.0.113.1");

        assertThat(send("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(429);
        assertThat(send("POST", "/api/auth/login", "203.0.113.2").getStatus()).isEqualTo(200);
    }

    /**
     * 【何のため】POST 以外や、ほかの URL は数えないことを確かめる（何回呼んでも断らない）。
     */
    @Test
    void POST以外とほかのURLは数えない() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertThat(send("GET", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(200);
            assertThat(send("POST", "/api/auth/logout", "203.0.113.1").getStatus()).isEqualTo(200);
            // 後ろに「/」が付いた URL も、決まりの URL とは別として扱う（Spring でも、別の URL になる）
            assertThat(send("POST", "/api/auth/login/", "203.0.113.1").getStatus()).isEqualTo(200);
        }
        // ここまでで、ログインの回数は1回も数えていない
        sendTimes(20, "/api/auth/login", "203.0.113.1");
    }

    /**
     * 【何のため】回数制限を使わない設定（開発・自動テスト）のときは、何回でも通すことを確かめる。
     */
    @Test
    void 使わない設定なら何回でも通す() throws Exception {
        filter = newFilter(false);

        sendTimes(50, "/api/auth/login", "203.0.113.1");
    }

    /**
     * 【何のため】数えた回数の入れ物が大きくなったら（1万件）、終わった区切りの回数を片付けることを確かめる
     * （片付けないと、たくさんの IP アドレスから試されたときに、メモリを使い続けてしまう）。
     * 入れ物はクラスの外から見えない（private）ので、ReflectionTestUtils（テスト用の道具）で中を見る。
     */
    @Test
    void 回数の入れ物が大きくなったら古い区切りを片付ける() throws Exception {
        for (int i = 0; i < 10_000; i++) {
            send("POST", "/api/auth/login", "10.0." + (i / 256) + "." + (i % 256));
        }
        Map<?, ?> counters = (Map<?, ?>) ReflectionTestUtils.getField(filter, "counters");
        assertThat(counters).hasSize(10_000);

        // 次の区切りで1回送ると、前の区切りの1万件が片付けられ、今の区切りの1件だけが残る
        filter.setClock(clockAt(WINDOW_START + 600));
        send("POST", "/api/auth/login", "203.0.113.1");

        assertThat(counters).hasSize(1);
    }
}
