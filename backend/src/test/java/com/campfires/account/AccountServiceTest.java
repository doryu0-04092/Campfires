package com.campfires.account;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.campfires.auth.PasswordResetTokenMapper;
import com.campfires.event.ApplicationCancelService;

/**
 * 【役割】自分のアカウント（/api/me）の業務の処理（AccountService）の単体テスト。
 * パスワードの変更（要件定義書 5.13）の、本人の確認と「変えた日時」の記録も確かめる。
 * 【なぜ必要か】アカウントの削除（要件定義書 5.5）は、「削除できる条件」を確かめる順番と、
 * 「削除したときの処理」（退会 → 参加申請の却下 → 申込みの取り消し → 個人情報の消去）の順番が大事。
 * 条件に合わないときに、何も書き換えないことも含めて、データベースなしで確かめる。
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final long USER = 7;

    @Mock
    private UserMapper userMapper;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private ApplicationCancelService cancelService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordResetTokenMapper passwordResetTokenMapper;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userMapper, accountMapper, cancelService, passwordEncoder,
                passwordResetTokenMapper);
    }

    /** 【何のため】テストで使うユーザーを作る。deleted が true なら、削除済み。 */
    private static User user(boolean deleted) {
        return deleted
                ? new User(USER, null, null, null, null, Instant.EPOCH, Instant.EPOCH, null)
                : new User(USER, "山田 太郎", "たろう", "taro@example.com", "hash", null, Instant.EPOCH, null);
    }

    /** 【何のため】削除の前の確認（ユーザーとパスワード）に通るように、偽物に教える。 */
    private void givenActiveUserWithPassword() {
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(false));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
    }

    /** 【何のため】削除の処理（書き換え）が、1つも行われていないことを確かめる。 */
    private void verifyNothingDeleted() {
        verify(accountMapper, never()).deleteMembershipsExceptOwner(anyLong());
        verify(accountMapper, never()).rejectPendingJoinRequests(anyLong());
        verifyNoInteractions(cancelService);
        verify(userMapper, never()).markDeleted(anyLong());
        verify(passwordResetTokenMapper, never()).deleteByUserId(anyLong());
    }

    /**
     * 【何のため】ユーザーがいない、または削除済みなら、どの操作も 401 になることを確かめる（ほかの端末で削除したときなど）。
     */
    @Test
    void 削除済みのユーザーは401() {
        when(userMapper.findById(USER)).thenReturn(user(true));

        assertApiError(() -> service.getMe(USER), 401, "UNAUTHENTICATED");
        assertApiError(() -> service.updateNickname(USER, "じろう"), 401, "UNAUTHENTICATED");
        assertApiError(() -> service.delete(USER, "password"), 401, "UNAUTHENTICATED");
        verify(userMapper, never()).updateNickname(anyLong(), anyString());
        verifyNothingDeleted();
    }

    /**
     * 【何のため】パスワードが違えば INVALID_PASSWORD で、削除の条件を調べる前に止まることを確かめる。
     */
    @Test
    void パスワードが違えば削除できない() {
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(false));

        assertApiError(() -> service.delete(USER, "wrong"), 400, "INVALID_PASSWORD");
        verifyNoInteractions(accountMapper);
        verifyNothingDeleted();
    }

    /**
     * 【何のため】アーカイブしていない、オーナーのコミュニティがあれば、削除できないことを確かめる。
     */
    @Test
    void オーナーのコミュニティが残っていれば削除できない() {
        givenActiveUserWithPassword();
        when(accountMapper.countActiveOwnedCommunities(USER)).thenReturn(1);

        assertApiError(() -> service.delete(USER, "password"), 409, "OWNS_ACTIVE_COMMUNITIES");
        verifyNothingDeleted();
    }

    /**
     * 【何のため】アーカイブ済みでも、開催前のイベントが残っていれば、削除できないことを確かめる。
     */
    @Test
    void 開催前のイベントが残っていれば削除できない() {
        givenActiveUserWithPassword();
        when(accountMapper.existsUpcomingEventInOwnedCommunities(USER)).thenReturn(true);

        assertApiError(() -> service.delete(USER, "password"), 409, "OWNED_COMMUNITY_HAS_UPCOMING_EVENTS");
        verifyNothingDeleted();
    }

    /**
     * 【何のため】削除できるときは、退会 → 参加申請の却下 → 申込みの取り消し（すべてのコミュニティ）→ 個人情報の消去、
     * の順番で処理することを確かめる。取り消しの操作をしたのは本人。
     */
    @Test
    void 決めた順番で削除する() {
        givenActiveUserWithPassword();

        service.delete(USER, "password");

        InOrder order = inOrder(accountMapper, cancelService, passwordResetTokenMapper, userMapper);
        order.verify(accountMapper).deleteMembershipsExceptOwner(USER);
        order.verify(accountMapper).rejectPendingJoinRequests(USER);
        // isNull() は「null が渡されたこと」を確かめる（null ＝ すべてのコミュニティ）
        order.verify(cancelService).cancelUpcomingApplications(eq(USER),
                eq(USER), isNull());
        // パスワードの再設定のトークンも消してから、個人情報を消す
        order.verify(passwordResetTokenMapper).deleteByUserId(USER);
        order.verify(userMapper).markDeleted(USER);
    }

    // ---------- パスワードの変更 ----------

    /**
     * 【何のため】今のパスワードが違えば、currentPassword の項目のエラー（INVALID_PASSWORD）になり、何も書き換えないことを確かめる。
     */
    @Test
    void 今のパスワードが違えばパスワードを変更できない() {
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(false));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertApiError(() -> service.changePassword(USER, "wrong", "new-password"), 400, "INVALID_PASSWORD");
        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
        verifyNoInteractions(passwordResetTokenMapper);
    }

    /**
     * 【何のため】今と同じパスワードには変えられない（newPassword の項目の VALIDATION_ERROR）ことを確かめる。
     */
    @Test
    void 今と同じパスワードには変更できない() {
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(false));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);

        assertApiError(() -> service.changePassword(USER, "password", "password"), 400, "VALIDATION_ERROR");
        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    /**
     * 【何のため】削除済みのユーザーは、パスワードを変更できない（401）ことを確かめる。
     */
    @Test
    void 削除済みのユーザーはパスワードを変更できない() {
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(true));

        assertApiError(() -> service.changePassword(USER, "password", "new-password"), 401, "UNAUTHENTICATED");
        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    /**
     * 【何のため】パスワードを変えると、変換した値と「変えた日時」（ミリ秒にそろえた、今の日時）を保存し、
     * 使っていない再設定のリンクを無効にすることを確かめる。
     */
    @Test
    void パスワードを変更すると変えた日時を記録して再設定のリンクを無効にする() {
        when(userMapper.findByIdForUpdate(USER)).thenReturn(user(false));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        Instant before = Instant.ofEpochMilli(System.currentTimeMillis());

        service.changePassword(USER, "password", "new-password");

        // ArgumentCaptor は「呼ばれたときに渡された値」を受け取って、あとで確かめるための道具
        ArgumentCaptor<Instant> changedAt = ArgumentCaptor.forClass(Instant.class);
        verify(userMapper).updatePassword(eq(USER), eq("new-hash"), changedAt.capture());
        assertThat(changedAt.getValue()).isBetween(before, Instant.ofEpochMilli(System.currentTimeMillis()));
        // ミリ秒より細かい部分がない（セッションを作った日時のミリ秒と、そのまま比べられる）
        assertThat(changedAt.getValue().getNano() % 1_000_000).isZero();
        verify(passwordResetTokenMapper).invalidateUnusedByUserId(USER);
    }

    /**
     * 【何のため】ニックネームを変えたら、変えた後のユーザーを読み直して返すことを確かめる。
     */
    @Test
    void ニックネームを変えたら読み直す() {
        when(userMapper.findById(USER)).thenReturn(user(false));

        service.updateNickname(USER, "じろう");

        InOrder order = inOrder(userMapper);
        order.verify(userMapper).findById(USER);
        order.verify(userMapper).updateNickname(USER, "じろう");
        order.verify(userMapper).findById(USER);
    }
}
