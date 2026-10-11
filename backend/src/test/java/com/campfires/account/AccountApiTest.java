package com.campfires.account;

import static org.assertj.core.api.Assertions.assertThat;
import static com.campfires.CsrfTestSupport.csrfToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.campfires.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/**
 * 【役割】自分のアカウントの API（/api/me）のテスト。
 * 【なぜ必要か】ニックネームの変更と、アカウントの削除のルール（要件定義書 5.5、5.6）のとおりに動くことを確かめるため。
 * 特にアカウントの削除は、退会、申込みの取り消し、キャンセル待ちの繰り上げ、お知らせ、と影響が広いので、まとめて確かめる。
 *
 * コミュニティやイベントの API はまだないので、テストのデータは SQL で直接作る（JdbcTemplate）。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountApiTest {

    private static final String PASSWORD = "campfire2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    // ---------- テストで使う、短く書くためのメソッド ----------

    /** 【何のため】ログインした状態のユーザー（番号とセッション）。 */
    private record Account(long id, String email, MockHttpSession session) {
    }

    /** 【何のため】アカウントを登録して、ログインした状態のセッションを返す。 */
    private Account newAccount() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/signup").with(csrfToken()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("realName", "山田 太郎", "nickname", "たろう",
                        "email", email, "password", PASSWORD, "agreedToPolicies", true))))
                .andExpect(status().isCreated());
        long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new Account(id, email, session);
    }

    /** 【何のため】アカウントの削除の API を呼ぶ。 */
    private ResultActions deleteMe(Account account, String password) throws Exception {
        return mockMvc.perform(delete("/api/me").with(csrfToken()).session(account.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("password", password))));
    }

    /** 【何のため】コミュニティを作り、オーナーをメンバーに入れて、番号を返す。 */
    private long createCommunity(long ownerId, boolean archived) {
        long id = jdbcTemplate.queryForObject(
                "INSERT INTO communities (name, owner_id, archived_at) VALUES ('テスト', ?, "
                        + (archived ? "now()" : "NULL") + ") RETURNING id",
                Long.class, ownerId);
        addMember(id, ownerId, "owner");
        return id;
    }

    /** 【何のため】メンバーを入れる。 */
    private void addMember(long communityId, long userId, String role) {
        jdbcTemplate.update("INSERT INTO memberships (community_id, user_id, role) VALUES (?, ?, ?)",
                communityId, userId, role);
    }

    /**
     * 【何のため】イベントを作って、番号を返す。日時は「今から何日後か」で指定する（マイナスなら過去）。
     */
    private long createEvent(long communityId, int capacity, int deadlineDays, int startDays, int endDays) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO events (community_id, title, start_at, end_at, application_deadline, location, capacity)
                VALUES (?, 'テストのイベント', now() + make_interval(days => ?), now() + make_interval(days => ?),
                        now() + make_interval(days => ?), 'オンライン', ?)
                RETURNING id
                """, Long.class, communityId, startDays, endDays, deadlineDays, capacity);
    }

    /** 【何のため】申込みを作る。appliedMinutes は申し込んだ順番（小さいほど先）。 */
    private void apply(long eventId, long userId, String status, int appliedMinutes) {
        jdbcTemplate.update("""
                INSERT INTO applications (event_id, user_id, status, applied_at)
                VALUES (?, ?, ?, now() - interval '1 day' + make_interval(mins => ?))
                """, eventId, userId, status, appliedMinutes);
    }

    /** 【何のため】申込みの状態を返す。申込みがなければ null。 */
    private String applicationStatus(long eventId, long userId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT status FROM applications WHERE event_id = ? AND user_id = ?", String.class, eventId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---------- 取得と、ニックネームの変更 ----------

    /**
     * 【何のため】ニックネームを変えると、前後の空白を除いた新しいニックネームが返ることを確かめる。
     */
    @Test
    void ニックネームを変更できる() throws Exception {
        Account me = newAccount();
        mockMvc.perform(patch("/api/me").with(csrfToken()).session(me.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"  じろう \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("じろう"));
        mockMvc.perform(get("/api/me").session(me.session()))
                .andExpect(jsonPath("$.nickname").value("じろう"))
                .andExpect(jsonPath("$.realName").value("山田 太郎"));
    }

    /**
     * 【何のため】空のニックネームには変えられないことを確かめる。
     */
    @Test
    void 空のニックネームには変更できない() throws Exception {
        Account me = newAccount();
        mockMvc.perform(patch("/api/me").with(csrfToken()).session(me.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"　\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    // ---------- アカウントの削除：できないとき ----------

    /**
     * 【何のため】パスワードが違えば、password の項目のエラー付きで 400 になることを確かめる。
     */
    @Test
    void パスワードが違えば削除できない() throws Exception {
        Account me = newAccount();
        deleteMe(me, "wrong-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    /**
     * 【何のため】アーカイブしていないコミュニティのオーナーなら、削除できないことを確かめる。
     */
    @Test
    void アーカイブしていないコミュニティのオーナーは削除できない() throws Exception {
        Account me = newAccount();
        createCommunity(me.id(), false);
        deleteMe(me, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNS_ACTIVE_COMMUNITIES"));
    }

    /**
     * 【何のため】アーカイブ済みでも、開催前のイベントが残っていれば、削除できないことを確かめる。
     */
    @Test
    void オーナーのコミュニティに開催前のイベントがあれば削除できない() throws Exception {
        Account me = newAccount();
        long communityId = createCommunity(me.id(), true);
        createEvent(communityId, 10, 1, 2, 3);
        deleteMe(me, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNED_COMMUNITY_HAS_UPCOMING_EVENTS"));
    }

    // ---------- アカウントの削除：できるとき ----------

    /**
     * 【何のため】削除すると、要件定義書 5.5 のとおりに処理されることを確かめる。
     * - 個人情報が消え、行は残る
     * - コミュニティから退会する（アーカイブ済みのコミュニティのオーナーの行は残る）
     * - 開催前のイベントへの申込みが取り消され、キャンセル待ちの人が繰り上がる
     * - 終了したイベントの申込みの記録は残る
     * - ログアウトした状態になり、もうログインできない。同じメールアドレスで、もう一度登録できる
     */
    @Test
    void 削除すると個人情報が消えて申込みが取り消される() throws Exception {
        Account me = newAccount();
        Account owner = newAccount();
        Account waiting = newAccount();

        // 自分がオーナーの、アーカイブ済みのコミュニティ（終了したイベントだけ）
        long myArchived = createCommunity(me.id(), true);
        createEvent(myArchived, 10, -10, -9, -8);

        // ほかの人のコミュニティに、メンバーとして参加している
        long communityId = createCommunity(owner.id(), false);
        addMember(communityId, me.id(), "member");
        addMember(communityId, waiting.id(), "member");

        // 定員1人のイベントに申し込み済み。ほかの人がキャンセル待ち
        long upcoming = createEvent(communityId, 1, 1, 2, 3);
        apply(upcoming, me.id(), "applied", 0);
        apply(upcoming, waiting.id(), "waitlisted", 1);
        // 終了したイベントの申込み
        long ended = createEvent(communityId, 10, -5, -4, -3);
        apply(ended, me.id(), "applied", 0);

        deleteMe(me, PASSWORD).andExpect(status().isNoContent());

        // 個人情報が消え、行は残る
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", me.id());
        assertThat(row.get("real_name")).isNull();
        assertThat(row.get("nickname")).isNull();
        assertThat(row.get("email")).isNull();
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();

        // メンバーの行は、アーカイブ済みのコミュニティのオーナーの行だけが残る
        assertThat(jdbcTemplate.queryForList(
                "SELECT community_id FROM memberships WHERE user_id = ?", Long.class, me.id()))
                .containsExactly(myArchived);

        // 開催前の申込みは取り消され、キャンセル待ちの人が繰り上がる。終了したイベントの記録は残る
        assertThat(applicationStatus(upcoming, me.id())).isNull();
        assertThat(applicationStatus(upcoming, waiting.id())).isEqualTo("applied");
        assertThat(applicationStatus(ended, me.id())).isEqualTo("applied");

        // ログアウトした状態になり、もうログインできない
        assertThat(me.session().isInvalid()).isTrue();
        mockMvc.perform(post("/api/auth/login").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("email", me.email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        // 同じメールアドレスで、もう一度登録できる
        mockMvc.perform(post("/api/auth/signup").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("realName", "山田 太郎", "nickname", "たろう",
                        "email", me.email(), "password", PASSWORD, "agreedToPolicies", true))))
                .andExpect(status().isCreated());
    }

    /**
     * 【何のため】締め切り後のイベントの申込みが取り消されたとき、繰り上げはせず、
     * オーナーと運営スタッフにお知らせが届くことを確かめる（要件定義書 5.2、5.10）。
     */
    @Test
    void 締め切り後の申込みが取り消されるとオーナーと運営スタッフに知らせる() throws Exception {
        Account me = newAccount();
        Account owner = newAccount();
        Account staff = newAccount();
        Account waiting = newAccount();

        long communityId = createCommunity(owner.id(), false);
        addMember(communityId, staff.id(), "staff");
        addMember(communityId, me.id(), "member");
        addMember(communityId, waiting.id(), "member");

        // 締め切りは過ぎたが、まだ終わっていないイベント
        long eventId = createEvent(communityId, 1, -1, 1, 2);
        apply(eventId, me.id(), "applied", 0);
        apply(eventId, waiting.id(), "waitlisted", 1);

        deleteMe(me, PASSWORD).andExpect(status().isNoContent());

        // 締め切り後なので、繰り上げない
        assertThat(applicationStatus(eventId, waiting.id())).isEqualTo("waitlisted");
        // オーナーと運営スタッフに、締め切り後のキャンセルのお知らせが届く
        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "SELECT user_id, type, actor_user_id, event_id FROM notifications WHERE event_id = ? ORDER BY user_id",
                eventId);
        assertThat(notifications).extracting(n -> n.get("user_id"))
                .containsExactlyInAnyOrder(owner.id(), staff.id());
        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.get("type")).isEqualTo("late_cancellation");
            assertThat(n.get("actor_user_id")).isEqualTo(me.id());
        });
    }
}
