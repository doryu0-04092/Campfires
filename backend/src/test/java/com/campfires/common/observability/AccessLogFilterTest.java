package com.campfires.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;

/**
 * 【役割】アクセスログ（AccessLogFilter）の単体テスト。Spring を起動せずに、フィルターだけを確かめる。
 * 【なぜ必要か】本番の見張り（CloudWatch のメトリクスとアラーム、infra/monitoring.tf）は、このログの項目の名前
 * （status、durationMs など）を使って数字を作る。項目の名前や、残す・残さないの決まりが変わると、見張りが効かなくなるため。
 * また、パスワードや検索の言葉（URL の ? より後ろ）を、ログに残さないことも確かめる。
 */
class AccessLogFilterTest {

    // ログを横取りして、中身を確かめるための入れ物（ListAppender は、書かれたログを List にためる）
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("campfires.access");
    private final AccessLogFilter filter = new AccessLogFilter();

    @BeforeEach
    void setUp() {
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        accessLogger.detachAppender(appender);
    }

    /** 【何のため】1行のログの、名前の付いた項目（key-value）を、Map にして返す */
    private Map<String, Object> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
    }

    @Test
    @DisplayName("API の呼び出しを、決めた項目で1行に残す（ログインしている人の番号も）")
    void logsApiRequestWithFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/communities");
        request.setQueryString("q=秘密の検索");
        request.setRemoteAddr("203.0.113.5");
        request.setContent("{\"password\":\"secret-password\"}".getBytes());
        // ActiveUserFilter が入れる、ログインしている人の番号のかわり
        request.setAttribute(AccessLogFilter.USER_ID_ATTRIBUTE, 42L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        Map<String, Object> fields = keyValues(event);
        assertThat(fields).containsEntry("method", "POST")
                .containsEntry("path", "/api/communities")
                .containsEntry("status", 201)
                .containsEntry("clientIp", "203.0.113.5")
                .containsEntry("userId", 42L)
                .containsKey("durationMs");
        // 検索の言葉（? より後ろ）と、送られてきた中身（パスワード）は、ログに出さない
        String everything = event.getFormattedMessage() + fields;
        assertThat(everything).doesNotContain("秘密の検索").doesNotContain("secret-password");
    }

    @Test
    @DisplayName("ログインしていなければ、userId を出さない")
    void omitsUserIdWhenAnonymous() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/communities"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(keyValues(appender.list.get(0))).doesNotContainKey("userId");
    }

    @Test
    @DisplayName("500 番台は WARN で残す（エラーのログとして目立たせる）")
    void serverErrorIsWarn() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(503);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/me"), response, new MockFilterChain());

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValues(appender.list.get(0))).containsEntry("status", 503);
    }

    @Test
    @DisplayName("処理の途中で例外が出たら、500 として残し、例外はそのまま外へ投げる")
    void exceptionIsLoggedAs500() {
        // MockFilterChain に、例外を投げるだけのサーブレットを渡す（new 型() { … } は、その場で中身を書いたクラスを作る書き方）
        MockFilterChain failingChain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                    jakarta.servlet.http.HttpServletResponse res) throws ServletException {
                throw new ServletException("テスト用の例外");
            }
        });

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/me"),
                new MockHttpServletResponse(), failingChain)).isInstanceOf(ServletException.class);
        assertThat(keyValues(appender.list.get(0))).containsEntry("status", 500);
    }

    @Test
    @DisplayName("画面のファイルと、うまくいった死活の確認は残さない。失敗した死活の確認は残す")
    void shouldLogRules() {
        assertThat(AccessLogFilter.shouldLog("/api/communities", 200)).isTrue();
        assertThat(AccessLogFilter.shouldLog("/assets/index.js", 200)).isFalse();
        assertThat(AccessLogFilter.shouldLog("/", 200)).isFalse();
        assertThat(AccessLogFilter.shouldLog("/api/health", 200)).isFalse();
        assertThat(AccessLogFilter.shouldLog("/api/health", 503)).isTrue();
        // /api で始まっても、/api/ でなければ API ではない
        assertThat(AccessLogFilter.shouldLog("/apix", 200)).isFalse();
    }
}
