package com.campfires.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 【役割】データベースが止まったとき（異常系）に、アプリが安全に失敗し、データベースが戻ったら元どおり動くことを確かめるテスト。
 * 【なぜ必要か】本番では、データベースの再起動や、ネットワークの不調で、一時的にデータベースにつながらないことがある。
 * そのときに、(1) 死活監視（GET /api/health）が 503 を返してロードバランサーに知らせること、
 * (2) API が、内部の情報（SQL の文や、例外のクラスの名前）を出さずに、決まった形のエラー（500、INTERNAL_ERROR）を返すこと、
 * (3) 返事がいつまでも返ってこない（固まる）のではなく、短い時間で返ること、
 * (4) データベースが戻ったら、アプリを再起動しなくても、また使えるようになること、を確かめる。
 *
 * 作り方の注意：
 * - このテストは、データベースを一時停止（pause）するので、ほかのテストと同じデータベースは使わない。
 *   このクラス専用の PostgreSQL（Testcontainers）を用意する。
 * - 本物のサーバーを、空いている番号（RANDOM_PORT）で起動し、本物の HTTP で呼ぶ。
 *   フィルター（ActiveUserFilter など）の中で起きたエラーも、本番と同じ流れで確かめるため。
 * - データベースにつながる待ち時間（Hikari のタイムアウト）を短くして、テストが早く終わるようにする。
 * - @DirtiesContext を付けて、このテストで使った Spring の部品は、テストのあとに捨てる（ほかのテストで使い回さない）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // データベースから接続をもらうまでの待ち時間の上限（ミリ秒）。ふだんは 30 秒
        "spring.datasource.hikari.connection-timeout=2000",
        // 接続がまだ使えるかを確かめるときの待ち時間の上限（ミリ秒）。ふだんは 5 秒
        "spring.datasource.hikari.validation-timeout=1000" })
@DirtiesContext
class DatabaseOutageTest {

    /**
     * このクラス専用のデータベース。static にすると、クラスの中のテストで1つを共有する。
     * @Container を付けると、Testcontainers が、テストの前に起動し、あとで片付ける。
     * @ServiceConnection を付けると、Spring Boot が、このデータベースへのつなぎ先を自動で設定する。
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    // 1回の呼び出しで待つ時間の上限。データベースが止まっていても、これより早く返事が来ることを確かめる
    private static final Duration BOUND = Duration.ofSeconds(10);

    // サーバーが起動した番号（RANDOM_PORT で決まった番号を、Spring が入れてくれる）
    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    // 本物の HTTP で呼ぶための道具（Java に入っているもの）
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 【何のため】呼び出しの結果と、かかった時間をまとめたもの。 */
    private record Result(int status, String contentType, String body, Duration elapsed) {
    }

    /**
     * 【何のため】テストが途中で失敗しても、データベースの一時停止を必ず解除する（後片付け）。
     */
    @AfterEach
    void resumeDatabase() {
        if (isPaused()) {
            unpauseDatabase();
        }
    }

    @Test
    @DisplayName("データベースが止まると、死活監視は 503、API は内部の情報なしの 500 を短い時間で返し、戻ると元どおり動く")
    void データベースが止まっても安全に失敗し戻ると回復する() throws Exception {
        // ---------- 1. ふだん（データベースが動いている）----------
        String session = signupAndGetSession();
        assertThat(get("/api/health", null).status()).isEqualTo(200);
        assertThat(get("/api/communities", null).status()).isEqualTo(200);
        assertThat(get("/api/me", session).status()).isEqualTo(200);

        // ---------- 2. データベースを一時停止する ----------
        pauseDatabase();
        // Hikari は「直前（0.5 秒以内）に使った接続」は確かめずに渡すので、少し待ってから呼ぶ
        // （そうしないと、確かめずに渡された接続で、データベースの返事を待ち続けることがある）
        Thread.sleep(1000);

        // 死活監視：503 と {"status":"unavailable"} を返す（ロードバランサーが、この箱に通信を送らなくなる）
        Result health = get("/api/health", null);
        assertThat(health.status()).isEqualTo(503);
        assertThat(jsonMapper.readTree(health.body()).path("status").asString()).isEqualTo("unavailable");
        assertThat(health.elapsed()).isLessThan(BOUND);

        // ログインなしで見られる API（Controller の中でエラーになる）
        assertSafeInternalError(get("/api/communities", null));
        // ログインが必要な API（Controller より前のフィルターで、アカウントを確かめるときにエラーになる）
        assertSafeInternalError(get("/api/me", session));

        // ---------- 3. データベースを戻す ----------
        unpauseDatabase();
        // 戻ってから、接続が作り直されるまで、少しの間待つ（最大 30 秒、0.5 秒ごとに確かめる）
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (get("/api/health", null).status() != 200 && System.nanoTime() < deadline) {
            Thread.sleep(500);
        }
        assertThat(get("/api/health", null).status()).isEqualTo(200);
        // アプリを再起動しなくても、API がまた使える（ログインの状態も残っている）
        assertThat(get("/api/communities", null).status()).isEqualTo(200);
        assertThat(get("/api/me", session).status()).isEqualTo(200);
    }

    /**
     * 【何のため】エラーの返事が「500、Problem Details の形、code は INTERNAL_ERROR、内部の情報なし、短い時間で返る」ことを確かめる。
     */
    private void assertSafeInternalError(Result result) throws Exception {
        assertThat(result.status()).isEqualTo(500);
        assertThat(result.contentType()).startsWith("application/problem+json");
        JsonNode body = jsonMapper.readTree(result.body());
        assertThat(body.path("code").asString()).isEqualTo("INTERNAL_ERROR");
        // 内部の情報（SQL、例外のクラスの名前、ライブラリの名前、スタックトレース）が、返事に入っていない
        assertThat(result.body()).doesNotContainIgnoringCase("select")
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("sql")
                .doesNotContainIgnoringCase("hikari")
                .doesNotContainIgnoringCase("postgres")
                .doesNotContain("trace")
                .doesNotContain("at com.");
        assertThat(result.elapsed()).isLessThan(BOUND);
    }

    // ---------- 道具 ----------

    /**
     * 【何のため】アカウントを登録して、ログインした状態の Cookie（JSESSIONID=…）を返す。
     * データを変える API なので、CSRF の合言葉を、Cookie とヘッダーの両方に入れて送る。
     */
    private String signupAndGetSession() throws Exception {
        String csrf = UUID.randomUUID().toString();
        String json = jsonMapper.writeValueAsString(Map.of("realName", "山田 太郎", "nickname", "たろう",
                "email", "outage-" + UUID.randomUUID() + "@example.com", "password", "campfire2026",
                "agreedToPolicies", true));
        HttpRequest request = HttpRequest.newBuilder(uri("/api/auth/signup"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        // Set-Cookie のヘッダーから、JSESSIONID=… の部分（「;」の前まで）を取り出す
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    /**
     * 【何のため】GET で API を呼び、結果とかかった時間を返す。cookie が null なら、ログインしていない人として呼ぶ。
     * 返事が来ないときに、テストがいつまでも止まらないよう、上限（15 秒）を付ける。
     */
    private Result get(String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        long start = System.nanoTime();
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        return new Result(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                response.body(), elapsed);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** 【何のため】データベースの箱（コンテナ）を一時停止する。プロセスが止まり、つないでも返事が来なくなる。 */
    private static void pauseDatabase() {
        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
    }

    /** 【何のため】一時停止を解除して、データベースを元に戻す。 */
    private static void unpauseDatabase() {
        postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
    }

    /** 【何のため】データベースの箱が、今、一時停止しているかを調べる。 */
    private static boolean isPaused() {
        // Boolean.TRUE.equals(…) は、値が null でも安全に「true かどうか」を確かめる書き方
        return Boolean.TRUE.equals(postgres.getDockerClient().inspectContainerCmd(postgres.getContainerId())
                .exec().getState().getPaused());
    }
}
