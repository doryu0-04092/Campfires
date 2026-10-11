package com.campfires.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.campfires.MapperTestBase;

/**
 * 【役割】ログインの失敗の記録の SQL（LoginFailureMapper.xml）を、本物の PostgreSQL で直接確かめるテスト。
 * 【なぜ必要か】ログインの試行制限（5回続けて失敗したら15分ロック。非機能要件 3章）は、1つの SQL
 * （INSERT … ON CONFLICT … DO UPDATE）の中で「数える・ロックする・ロックが解けたら数え直す」を行っている。
 * 4回目と5回目の境目、ロックが解けた後の数え直し、残り秒数の計算を、時刻を動かして確かめる。
 *
 * テストの中では now() が変わらないので、「ロックが解けた」状態は、locked_until を過去に書き換えて作る。
 */
class LoginFailureMapperSqlTest extends MapperTestBase {

    @Autowired
    private LoginFailureMapper loginFailureMapper;

    /** 【何のため】毎回ちがう、小文字のメールアドレスを作る。 */
    private static String email() {
        return "lock-" + UUID.randomUUID() + "@example.com";
    }

    /** 【何のため】続けて失敗した回数を返す。 */
    private int failureCount(String email) {
        return jdbcTemplate.queryForObject("SELECT failure_count FROM login_failures WHERE email = ?", Integer.class,
                email);
    }

    /** 【何のため】ロックが解ける日時を、今から何秒後かに書き換える（マイナスなら、もう解けている）。 */
    private void setLockedUntil(String email, int secondsFromNow) {
        jdbcTemplate.update(
                "UPDATE login_failures SET locked_until = now() + make_interval(secs => ?) WHERE email = ?",
                secondsFromNow, email);
    }

    /**
     * 【何のため】失敗の記録がなければ、ロックの状態は null（ロックしていない）であることを確かめる。
     */
    @Test
    void 記録がなければnull() {
        assertThat(loginFailureMapper.findLock(email())).isNull();
    }

    /**
     * 【何のため】4回目まではロックせず、5回目でちょうど15分（900秒）ロックすることを確かめる。
     */
    @Test
    void 五回目の失敗で15分ロックする() {
        String email = email();
        for (int i = 1; i <= 4; i++) {
            loginFailureMapper.recordFailure(email, 5, 15);
        }
        assertThat(failureCount(email)).isEqualTo(4);
        assertThat(loginFailureMapper.findLock(email)).isEqualTo(new LoginLock(false, 0));

        loginFailureMapper.recordFailure(email, 5, 15);

        assertThat(failureCount(email)).isEqualTo(5);
        assertThat(loginFailureMapper.findLock(email)).isEqualTo(new LoginLock(true, 900));
    }

    /**
     * 【何のため】残りの秒数は、切り上げて数えることを確かめる（あと 0.5 秒なら 1 秒。0 秒と答えて、すぐ試させない）。
     */
    @Test
    void 残りの秒数は切り上げる() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            loginFailureMapper.recordFailure(email, 5, 15);
        }
        jdbcTemplate.update(
                "UPDATE login_failures SET locked_until = now() + interval '500 milliseconds' WHERE email = ?", email);

        assertThat(loginFailureMapper.findLock(email)).isEqualTo(new LoginLock(true, 1));
    }

    /**
     * 【何のため】ロックが解ける日時がちょうど今なら、もうロックしていない（解けた）ことを確かめる。
     */
    @Test
    void ロックが解ける日時がちょうど今なら解けている() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            loginFailureMapper.recordFailure(email, 5, 15);
        }
        setLockedUntil(email, 0);

        assertThat(loginFailureMapper.findLock(email).locked()).isFalse();
    }

    /**
     * 【何のため】ロックが解けた後の失敗は、1回目から数え直し、ロックの日時も空に戻すことを確かめる。
     */
    @Test
    void ロックが解けた後は1回目から数え直す() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            loginFailureMapper.recordFailure(email, 5, 15);
        }
        setLockedUntil(email, -60);

        loginFailureMapper.recordFailure(email, 5, 15);

        assertThat(failureCount(email)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT locked_until IS NULL FROM login_failures WHERE email = ?",
                Boolean.class, email)).isTrue();
    }

    /**
     * 【何のため】ロックするまでの回数が1回なら、最初の失敗（INSERT の側）でロックすることを確かめる。
     */
    @Test
    void 回数が1回なら最初の失敗でロックする() {
        String email = email();

        loginFailureMapper.recordFailure(email, 1, 30);

        assertThat(loginFailureMapper.findLock(email)).isEqualTo(new LoginLock(true, 1800));
    }

    /**
     * 【何のため】ログインに成功したら、失敗の記録を消し、次は1回目から数えることを確かめる。
     * ほかのメールアドレスの記録は消さない。
     */
    @Test
    void 成功したら記録を消す() {
        String email = email();
        String other = email();
        loginFailureMapper.recordFailure(email, 5, 15);
        loginFailureMapper.recordFailure(other, 5, 15);

        loginFailureMapper.clear(email);

        assertThat(loginFailureMapper.findLock(email)).isNull();
        assertThat(loginFailureMapper.findLock(other)).isNotNull();
    }

    /**
     * 【何のため】ロックが解けた後の「残りの秒数」は、LoginLock の説明のとおり 0 であることを確かめる。
     * 以前の SQL は、解けた後の残りの秒数をマイナス（例：-60）で返していた（COALESCE は NULL のときだけ 0 にするため）。
     * GREATEST で 0 より小さくならないように直した。
     */
    @Test
    void ロックが解けた後の残りの秒数は0() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            loginFailureMapper.recordFailure(email, 5, 15);
        }
        setLockedUntil(email, -60);

        assertThat(loginFailureMapper.findLock(email)).isEqualTo(new LoginLock(false, 0));
    }
}
