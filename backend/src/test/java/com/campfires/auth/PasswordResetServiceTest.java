package com.campfires.auth;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.campfires.account.User;
import com.campfires.account.UserMapper;

/**
 * 【役割】パスワードの再設定の業務の処理（PasswordResetService）の単体テスト。
 * 【なぜ必要か】「機能を止めているときは 503」「登録されていなくても同じように処理する」「届けるのに失敗しても同じ返事」
 * 「トークンは変換した値だけを保存する」など、API のテストでは作りにくい場合や、外から見えにくいルールを、
 * 偽物の部品（Mockito）を使って、データベースなしで確かめるため。
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final long USER = 7;

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordResetTokenMapper tokenMapper;
    @Mock
    private LoginFailureMapper loginFailureMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordResetNotifier notifier;

    /** 【何のため】テストする PasswordResetService を作る。リンクの頭の URL は、最後に「/」を付けて渡す（取り除かれるか）。 */
    private PasswordResetService service(boolean enabled) {
        return new PasswordResetService(userMapper, tokenMapper, loginFailureMapper, passwordEncoder, notifier,
                enabled, "https://campfires.example.com/");
    }

    /** 【何のため】テストで使うユーザーを作る。メールアドレスには大文字を入れておく（小文字にそろえるかを確かめるため）。 */
    private static User user(boolean deleted) {
        return deleted
                ? new User(USER, null, null, null, null, Instant.EPOCH, Instant.EPOCH, null)
                : new User(USER, "山田 太郎", "たろう", "Taro@Example.com", "hash", null, Instant.EPOCH, null);
    }

    // ---------- 機能を止めているとき ----------

    /**
     * 【何のため】機能を止めているときは、依頼も再設定も 503（PASSWORD_RESET_UNAVAILABLE）になり、データベースに触れないことを確かめる。
     */
    @Test
    void 止めているときは503でデータベースに触れない() {
        PasswordResetService service = service(false);

        assertThat(service.isEnabled()).isFalse();
        assertApiError(() -> service.requestReset("taro@example.com"), 503, "PASSWORD_RESET_UNAVAILABLE");
        assertApiError(() -> service.resetPassword("token", "new-password"), 503, "PASSWORD_RESET_UNAVAILABLE");
        verifyNoInteractions(userMapper, tokenMapper, loginFailureMapper, notifier);
    }

    // ---------- 再設定の依頼 ----------

    /**
     * 【何のため】登録されていないメールアドレスなら、トークンを保存せず、何も届けず、エラーにもしないことを確かめる。
     */
    @Test
    void 登録されていなければ何も保存せず届けない() {
        when(userMapper.findActiveByEmail("nobody@example.com")).thenReturn(null);

        service(true).requestReset("nobody@example.com");

        verifyNoInteractions(tokenMapper, notifier);
    }

    /**
     * 【何のため】登録されていれば、古いトークンを無効にしてから、新しいトークンの「変換した値」を保存し、
     * トークン付きのリンクを届けることを確かめる。リンクのトークンを SHA-256 で変換すると、保存した値と一致する。
     */
    @Test
    void 登録されていれば変換した値を保存してリンクを届ける() {
        when(userMapper.findActiveByEmail("Taro@Example.com")).thenReturn(user(false));

        service(true).requestReset("Taro@Example.com");

        ArgumentCaptor<String> savedHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(tokenMapper, notifier);
        order.verify(tokenMapper).invalidateUnusedByUserId(USER);
        order.verify(tokenMapper).insert(eq(USER), savedHash.capture(), eq(30));
        order.verify(notifier).send(eq(user(false)), url.capture());

        // リンクは「頭の URL（最後の / は取り除く）+ /password/reset#token=トークン」の形。
        // トークンは「#」の後ろなので、画面を開いてもサーバーへは送られない
        assertThat(url.getValue()).startsWith("https://campfires.example.com/password/reset#token=");
        String token = url.getValue().substring(url.getValue().indexOf("token=") + "token=".length());
        // 32バイトの乱数を Base64URL（= なし）にすると 43文字。URL にそのまま入れられる文字だけ
        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
        // 保存したのはトークンそのものではなく、SHA-256 で変換した 16進数の64文字
        assertThat(savedHash.getValue()).isEqualTo(PasswordResetService.sha256Hex(token)).hasSize(64)
                .isNotEqualTo(token);
    }

    /**
     * 【何のため】届けるのに失敗しても、エラーを投げない（登録されていないときと同じ返事にする）ことを確かめる。
     */
    @Test
    void 届けるのに失敗しても同じ返事にする() {
        when(userMapper.findActiveByEmail("taro@example.com")).thenReturn(user(false));
        doThrow(new IllegalStateException("メールの送信に失敗")).when(notifier).send(any(), anyString());

        service(true).requestReset("taro@example.com");

        verify(tokenMapper).insert(eq(USER), anyString(), anyInt());
    }

    /**
     * 【何のため】SHA-256 の変換が、決まった値（"abc" の SHA-256 は、よく知られた値）になることを確かめる。
     */
    @Test
    void SHA256で16進数の64文字に変換する() {
        assertThat(PasswordResetService.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // ---------- 再設定 ----------

    /**
     * 【何のため】使えるトークンが見つからなければ 400（INVALID_RESET_TOKEN）で、何も書き換えないことを確かめる。
     */
    @Test
    void 使えるトークンがなければ400() {
        when(tokenMapper.findUsable(PasswordResetService.sha256Hex("unknown"))).thenReturn(null);

        assertApiError(() -> service(true).resetPassword("unknown", "new-password"), 400, "INVALID_RESET_TOKEN");
        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    /**
     * 【何のため】ロックを待っている間に、同じトークンが使われた（ロックした後に見つからない）なら 400 になることを確かめる。
     */
    @Test
    void ロックを待つ間に使われたトークンは400() {
        String hash = PasswordResetService.sha256Hex("token");
        when(tokenMapper.findUsable(hash)).thenReturn(new PasswordResetToken(1, USER));
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(false));
        when(tokenMapper.findUsableForUpdate(hash)).thenReturn(null);

        assertApiError(() -> service(true).resetPassword("token", "new-password"), 400, "INVALID_RESET_TOKEN");
        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    /**
     * 【何のため】ロックを待っている間に、アカウントが削除されたなら 400 になることを確かめる。
     */
    @Test
    void ロックを待つ間に削除されたアカウントは400() {
        String hash = PasswordResetService.sha256Hex("token");
        when(tokenMapper.findUsable(hash)).thenReturn(new PasswordResetToken(1, USER));
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(true));
        when(tokenMapper.findUsableForUpdate(hash)).thenReturn(new PasswordResetToken(1, USER));

        assertApiError(() -> service(true).resetPassword("token", "new-password"), 400, "INVALID_RESET_TOKEN");
        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    /**
     * 【何のため】再設定できるときは、ユーザー → トークンの順にロックしてから、パスワードと変えた日時を保存し、
     * トークンを無効にし、ログインのロックを解く（メールアドレスは小文字にそろえる）ことを確かめる。
     */
    @Test
    void 再設定するとパスワードを変えてトークンを無効にしロックを解く() {
        String hash = PasswordResetService.sha256Hex("token");
        when(tokenMapper.findUsable(hash)).thenReturn(new PasswordResetToken(1, USER));
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(false));
        when(tokenMapper.findUsableForUpdate(hash)).thenReturn(new PasswordResetToken(1, USER));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service(true).resetPassword("token", "new-password");

        InOrder order = inOrder(userMapper, tokenMapper, loginFailureMapper);
        order.verify(userMapper).findByIdForUpdate(USER);
        order.verify(tokenMapper).findUsableForUpdate(hash);
        order.verify(userMapper).updatePassword(eq(USER), eq("new-hash"), any(Instant.class));
        order.verify(tokenMapper).invalidateUnusedByUserId(USER);
        order.verify(loginFailureMapper).clear("taro@example.com");
    }
}
