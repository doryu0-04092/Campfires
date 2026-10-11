package com.campfires.auth;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.campfires.account.User;
import com.campfires.account.UserMapper;
import com.campfires.common.web.ApiException;

/**
 * 【役割】アカウントの登録とログインの業務の処理（AuthService）の単体テスト。
 * 【なぜ必要か】ログインの試行制限（非機能要件 3章）は、
 * 「ロック中ならパスワードを確かめもしない」「登録されていないメールアドレスでも、同じようにパスワードを確かめる
 * （かかる時間で、登録の有無を探られないように）」「メールアドレスは小文字にそろえて数える」など、
 * 外から見えにくいルールが多い。偽物の部品を使って、呼び出しの中身まで確かめる。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // 登録されていないメールアドレスのときに使う、ダミーの変換した値（偽物の PasswordEncoder が返す）
    private static final String DUMMY_HASH = "dummy-hash";

    @Mock
    private UserMapper userMapper;
    @Mock
    private LoginFailureMapper loginFailureMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        // AuthService は作られるときに、ダミーの値を1回だけ作る
        when(passwordEncoder.encode("dummy-password-for-timing")).thenReturn(DUMMY_HASH);
        service = new AuthService(userMapper, loginFailureMapper, passwordEncoder);
    }

    private static User user() {
        return new User(1, "山田 太郎", "たろう", "taro@example.com", "real-hash", null, Instant.EPOCH);
    }

    /**
     * 【何のため】ロック中なら 429（ACCOUNT_LOCKED）で、あと何秒かを Retry-After に入れ、パスワードを確かめもしないことを確かめる。
     */
    @Test
    void ロック中ならパスワードを確かめずに429() {
        when(loginFailureMapper.findLock("taro@example.com")).thenReturn(new LoginLock(true, 120));

        ApiException exception = catchThrowableOfType(ApiException.class,
                () -> service.login(new LoginRequest("taro@example.com", "campfire2026")));

        assertThat(exception.getStatus().value()).isEqualTo(429);
        assertThat(exception.getCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(exception.getHeaders()).containsEntry("Retry-After", "120");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(loginFailureMapper, never()).recordFailure(anyString(), anyInt(), anyInt());
    }

    /**
     * 【何のため】メールアドレスは小文字にそろえて、ロックの確認と失敗の記録に使うことを確かめる
     * （大文字を混ぜて試しても、同じアドレスとして数える）。
     */
    @Test
    void メールアドレスは小文字にそろえて数える() {
        assertApiError(() -> service.login(new LoginRequest("Taro@Example.COM", "wrong")), 401,
                "INVALID_CREDENTIALS");

        verify(loginFailureMapper).findLock("taro@example.com");
        verify(loginFailureMapper).recordFailure("taro@example.com", AuthService.MAX_LOGIN_FAILURES,
                AuthService.LOCK_MINUTES);
    }

    /**
     * 【何のため】ロックの記録があっても、ロックが解けていれば、ログインを試せることを確かめる。
     */
    @Test
    void ロックが解けていればログインできる() {
        when(loginFailureMapper.findLock("taro@example.com")).thenReturn(new LoginLock(false, 0));
        when(userMapper.findActiveByEmail("taro@example.com")).thenReturn(user());
        when(passwordEncoder.matches("campfire2026", "real-hash")).thenReturn(true);

        assertThat(service.login(new LoginRequest("taro@example.com", "campfire2026")).id()).isEqualTo(1);
        // 成功したら、失敗の記録を消す
        verify(loginFailureMapper).clear("taro@example.com");
        verify(loginFailureMapper, never()).recordFailure(anyString(), anyInt(), anyInt());
    }

    /**
     * 【何のため】登録されていないメールアドレスでも、ダミーの値でパスワードを確かめてから、失敗として数えることを確かめる。
     */
    @Test
    void 登録されていなくてもパスワードを確かめてから失敗にする() {
        assertApiError(() -> service.login(new LoginRequest("nobody@example.com", "campfire2026")), 401,
                "INVALID_CREDENTIALS");

        verify(passwordEncoder).matches("campfire2026", DUMMY_HASH);
        verify(loginFailureMapper).recordFailure("nobody@example.com", 5, 15);
        verify(loginFailureMapper, never()).clear(anyString());
    }

    /**
     * 【何のため】パスワードが違えば、登録されていないときと同じエラー（どちらが違うかを教えない）で、失敗として数えることを確かめる。
     */
    @Test
    void パスワードが違えば同じエラーで失敗にする() {
        when(userMapper.findActiveByEmail("taro@example.com")).thenReturn(user());

        assertApiError(() -> service.login(new LoginRequest("taro@example.com", "wrong")), 401, "INVALID_CREDENTIALS");
        verify(loginFailureMapper).recordFailure("taro@example.com", 5, 15);
    }

    /**
     * 【何のため】登録では、パスワードを変換した値で保存し、今の規約のバージョンを記録することを確かめる。
     */
    @Test
    void 登録ではパスワードを変換して保存する() {
        when(passwordEncoder.encode("campfire2026")).thenReturn("encoded");
        when(userMapper.insert("山田 太郎", "たろう", "taro@example.com", "encoded", AuthService.POLICIES_VERSION))
                .thenReturn(user());

        service.signup(new SignupRequest("山田 太郎", "たろう", "taro@example.com", "campfire2026", true));

        verify(userMapper).insert("山田 太郎", "たろう", "taro@example.com", "encoded", AuthService.POLICIES_VERSION);
    }

    /**
     * 【何のため】すでに登録されているメールアドレスなら、保存する前に EMAIL_ALREADY_REGISTERED にすることを確かめる。
     */
    @Test
    void 登録済みのメールアドレスは保存する前に409() {
        when(userMapper.findActiveByEmail("taro@example.com")).thenReturn(user());

        assertApiError(() -> service.signup(new SignupRequest("山田", "たろう", "taro@example.com", "campfire2026", true)),
                409, "EMAIL_ALREADY_REGISTERED");
        verify(passwordEncoder, never()).encode("campfire2026");
    }

    /**
     * 【何のため】確かめた直後に、ほかの人が同じメールアドレスで登録した（データベースの制約で止まった）ときも、
     * 同じ EMAIL_ALREADY_REGISTERED にすることを確かめる。
     */
    @Test
    void 同時に登録されても409() {
        when(passwordEncoder.encode("campfire2026")).thenReturn("encoded");
        when(userMapper.insert(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("users_email_unique"));

        assertApiError(() -> service.signup(new SignupRequest("山田", "たろう", "taro@example.com", "campfire2026", true)),
                409, "EMAIL_ALREADY_REGISTERED");
    }
}
