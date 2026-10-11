package com.campfires;

import static com.campfires.CsrfTestSupport.csrfToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.json.JsonMapper;

/**
 * 【役割】API のテストで共通に使う道具をまとめた、テストの土台のクラス。
 * 【なぜ必要か】「アカウントを作ってログインする」「JSON を送る」などを、テストごとに書かずに済むようにするため。
 * テストのクラスは「extends ApiTestBase」と書くと、ここのメソッドを使える。
 *
 * abstract は「このクラスだけでは使わず、土台にする」という印。
 * テストどうしで同じデータベースを使うので、メールアドレスなどは毎回ちがうもの（UUID でランダムに作る）にする。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    // テストで使うパスワード（開発用の偽の値）
    protected static final String PASSWORD = "campfire2026";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected JsonMapper jsonMapper;

    /**
     * 【何のため】ログインした状態のユーザー（番号、メールアドレス、セッション）。
     */
    protected record Account(long id, String email, MockHttpSession session) {
    }

    /**
     * 【何のため】アカウントを登録して、ログインした状態のユーザーを返す。
     */
    protected Account newAccount() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(withJson(post("/api/auth/signup"), Map.of("realName", "山田 太郎", "nickname", "たろう",
                "email", email, "password", PASSWORD, "agreedToPolicies", true)).session(session))
                .andExpect(status().isCreated());
        long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new Account(id, email, session);
    }

    /**
     * 【何のため】リクエストに、JSON の中身と CSRF トークンを付ける。
     */
    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body) {
        return builder.with(csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    /**
     * 【何のため】ログインしたユーザーとして、中身なしでデータを変える API を呼ぶためのリクエストを作る。
     */
    protected MockHttpServletRequestBuilder as(Account account, MockHttpServletRequestBuilder builder) {
        return builder.with(csrfToken()).session(account.session());
    }

    /**
     * 【何のため】ログインしたユーザーとして、JSON を送る API を呼ぶためのリクエストを作る。
     */
    protected MockHttpServletRequestBuilder as(Account account, MockHttpServletRequestBuilder builder, Object body) {
        return withJson(builder, body).session(account.session());
    }

    // ---------- テストのデータを SQL で直接作る（API を通すより速く、終了したイベントなども作れる） ----------

    /**
     * 【何のため】コミュニティを作り、オーナーをメンバーに入れて、番号を返す。
     */
    protected long insertCommunity(long ownerId, boolean archived) {
        long id = jdbcTemplate.queryForObject(
                "INSERT INTO communities (name, owner_id, archived_at) VALUES ('テストのコミュニティ', ?, "
                        + (archived ? "now()" : "NULL") + ") RETURNING id",
                Long.class, ownerId);
        insertMember(id, ownerId, "owner");
        return id;
    }

    /**
     * 【何のため】メンバーを入れる。role は owner、staff、member のどれか。
     */
    protected void insertMember(long communityId, long userId, String role) {
        jdbcTemplate.update("INSERT INTO memberships (community_id, user_id, role) VALUES (?, ?, ?)",
                communityId, userId, role);
    }

    /**
     * 【何のため】イベントを作って、番号を返す。日時は「今から何時間後か」で指定する（マイナスなら過去）。
     */
    protected long insertEvent(long communityId, String title, int capacity, int deadlineHours, int startHours,
            int endHours) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO events (community_id, title, start_at, end_at, application_deadline, location, capacity)
                VALUES (?, ?, now() + make_interval(hours => ?), now() + make_interval(hours => ?),
                        now() + make_interval(hours => ?), 'オンライン', ?)
                RETURNING id
                """, Long.class, communityId, title, startHours, endHours, deadlineHours, capacity);
    }

    /**
     * 【何のため】申込みを作る。minutesAgo は、何分前に申し込んだことにするか（大きいほど先に申し込んだ）。
     */
    protected void insertApplication(long eventId, long userId, String status, int minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO applications (event_id, user_id, status, applied_at)
                VALUES (?, ?, ?, now() - make_interval(mins => ?))
                """, eventId, userId, status, minutesAgo);
    }

    /**
     * 【何のため】申込みの状態を返す。申込みがなければ null。
     */
    protected String applicationStatus(long eventId, long userId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT status FROM applications WHERE event_id = ? AND user_id = ?", String.class, eventId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 【何のため】毎回ちがう、検索のテストに使う単語を作る（ほかのテストのデータと混ざらないように）。
     */
    protected static String uniqueWord() {
        return "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
