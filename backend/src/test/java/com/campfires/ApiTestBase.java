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

    /**
     * 【何のため】毎回ちがう、検索のテストに使う単語を作る（ほかのテストのデータと混ざらないように）。
     */
    protected static String uniqueWord() {
        return "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
