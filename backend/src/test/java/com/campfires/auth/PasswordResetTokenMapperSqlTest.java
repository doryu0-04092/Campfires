package com.campfires.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.campfires.MapperTestBase;
import com.campfires.account.UserMapper;

/**
 * 【役割】パスワードの再設定のトークンの SQL（PasswordResetTokenMapper.xml）と、パスワードの書き換えの SQL
 * （UserMapper.xml の updatePassword）を、本物の PostgreSQL で直接確かめるテスト。
 * 【なぜ必要か】「まだ使えるトークン」の条件（未使用・期限内・アカウントが削除されていない）は、1つの SQL の中で判断している。
 * 条件の1つずつを、データを書き換えて確かめる。
 *
 * テストの中では now() が変わらないので、期限切れは expires_at を過去に書き換えて作る。
 */
class PasswordResetTokenMapperSqlTest extends MapperTestBase {

    @Autowired
    private PasswordResetTokenMapper tokenMapper;

    @Autowired
    private UserMapper userMapper;

    /** 【何のため】毎回ちがう、トークンを変換した値の代わり（64文字）を作る。 */
    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    /** 【何のため】まだ使っていない（used_at が NULL の）トークンの数を返す。 */
    private int unusedCount(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL", Integer.class, userId);
    }

    /**
     * 【何のため】保存したトークンは、変換した値で探せて、ユーザーの番号が返ることを確かめる。期限は保存した時から30分。
     */
    @Test
    void 保存したトークンを探せる() {
        long userId = insertUser("たろう");
        String hash = hash();
        tokenMapper.insert(userId, hash, 30);

        PasswordResetToken found = tokenMapper.findUsable(hash);
        assertThat(found).isNotNull();
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(tokenMapper.findUsableForUpdate(hash)).isEqualTo(found);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT expires_at = now() + interval '30 minutes' FROM password_reset_tokens WHERE id = ?",
                Boolean.class, found.id())).isTrue();
        // 知らない値では見つからない
        assertThat(tokenMapper.findUsable(hash())).isNull();
    }

    /**
     * 【何のため】期限切れ（ちょうど今を含む）、使用済み、アカウントが削除されたトークンは、見つからないことを確かめる。
     */
    @Test
    void 使えないトークンは見つからない() {
        long userId = insertUser("たろう");
        String expired = hash();
        String used = hash();
        String deletedUsers = hash();
        tokenMapper.insert(userId, expired, 30);
        tokenMapper.insert(userId, used, 30);
        // 期限が「ちょうど今」なら、もう使えない（expires_at > now() なので）
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = now() WHERE token_hash = ?", expired);
        jdbcTemplate.update("UPDATE password_reset_tokens SET used_at = now() WHERE token_hash = ?", used);

        long deletedUserId = insertUser("じろう");
        tokenMapper.insert(deletedUserId, deletedUsers, 30);
        userMapper.markDeleted(deletedUserId);

        assertThat(tokenMapper.findUsable(expired)).isNull();
        assertThat(tokenMapper.findUsable(used)).isNull();
        assertThat(tokenMapper.findUsable(deletedUsers)).isNull();
        assertThat(tokenMapper.findUsableForUpdate(deletedUsers)).isNull();
    }

    /**
     * 【何のため】無効にするのは、そのユーザーの、まだ使っていないトークンだけで、ほかのユーザーのトークンは残ることを確かめる。
     */
    @Test
    void そのユーザーのトークンだけを無効にする() {
        long userId = insertUser("たろう");
        long otherId = insertUser("じろう");
        tokenMapper.insert(userId, hash(), 30);
        tokenMapper.insert(userId, hash(), 30);
        tokenMapper.insert(otherId, hash(), 30);

        tokenMapper.invalidateUnusedByUserId(userId);

        assertThat(unusedCount(userId)).isZero();
        assertThat(unusedCount(otherId)).isEqualTo(1);
    }

    /**
     * 【何のため】アカウントの削除のときに、そのユーザーのトークンだけを消すことを確かめる。
     */
    @Test
    void そのユーザーのトークンだけを消す() {
        long userId = insertUser("たろう");
        long otherId = insertUser("じろう");
        tokenMapper.insert(userId, hash(), 30);
        tokenMapper.insert(otherId, hash(), 30);

        tokenMapper.deleteByUserId(userId);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE user_id = ?",
                Integer.class, userId)).isZero();
        assertThat(unusedCount(otherId)).isEqualTo(1);
    }

    /**
     * 【何のため】パスワードを書き換えると、変換した値と、渡した「変えた日時」（ミリ秒）がそのまま保存され、
     * ユーザーを読むと passwordChangedAt に入っていることを確かめる。削除済みのユーザーは書き換えない。
     */
    @Test
    void パスワードと変えた日時を保存する() {
        long userId = insertUser("たろう");
        assertThat(userMapper.findById(userId).passwordChangedAt()).isNull();
        Instant changedAt = Instant.ofEpochMilli(1_790_000_000_123L);

        userMapper.updatePassword(userId, "new-hash", changedAt);

        assertThat(userMapper.findById(userId).passwordHash()).isEqualTo("new-hash");
        assertThat(userMapper.findById(userId).passwordChangedAt()).isEqualTo(changedAt);

        long deletedId = insertUser("じろう");
        userMapper.markDeleted(deletedId);
        userMapper.updatePassword(deletedId, "new-hash", changedAt);
        assertThat(userMapper.findById(deletedId).passwordHash()).isNull();
    }
}
