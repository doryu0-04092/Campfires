package com.campfires.account;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.auth.PasswordResetTokenMapper;
import com.campfires.common.security.PasswordChangeClock;
import com.campfires.common.web.ApiException;
import com.campfires.common.web.FieldErrorItem;
import com.campfires.event.ApplicationCancelService;

/**
 * 【役割】自分のアカウント（/api/me）の、業務の処理をするクラス。
 * 【なぜ必要か】情報の取得、ニックネームの変更、パスワードの変更、アカウントの削除（要件定義書 5.5、5.6、5.13）のルールを、
 * 1か所にまとめるため。
 */
@Service
public class AccountService {

    // アカウントの削除とパスワードの変更を、ログに残す（あとから、いつ行われたかを調べられるようにするため）。
    // パスワードは、ログに出さない
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final ApplicationCancelService applicationCancelService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenMapper passwordResetTokenMapper;

    public AccountService(UserMapper userMapper, AccountMapper accountMapper,
            ApplicationCancelService applicationCancelService, PasswordEncoder passwordEncoder,
            PasswordResetTokenMapper passwordResetTokenMapper) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.applicationCancelService = applicationCancelService;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenMapper = passwordResetTokenMapper;
    }

    /**
     * 【何のため】ログインしている自分の情報を返す。
     * セッションが残っていても、アカウントが削除されていれば 401（ほかの端末で削除したときなど）。
     */
    public User getMe(long userId) {
        return requireActive(userMapper.findById(userId));
    }

    /**
     * 【何のため】ニックネームを変える。ほかの表はユーザーの番号でつながっているので、
     * 過去の申込みの記録などにも、自動で新しいニックネームが表示される（要件定義書 5.6）。
     */
    @Transactional
    public User updateNickname(long userId, String nickname) {
        requireActive(userMapper.findById(userId));
        userMapper.updateNickname(userId, nickname);
        return userMapper.findById(userId);
    }

    /**
     * 【何のため】パスワードを変える（F-24、要件定義書 5.13）。本人の確認のため、今のパスワードも確かめる。
     * 変えた日時（passwordChangedAt）を記録するので、それより前に作られたセッション（ほかの端末のログイン）は、
     * 次の API の呼び出しで使えなくなる（ActiveUserFilter）。操作した端末は、Controller が新しいセッションに作り直す。
     */
    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        // ユーザーの行をロックして、同じアカウントのパスワードの変更・削除が、同時に行われないようにする
        User user = requireActive(userMapper.findByIdForUpdate(userId));

        // 本人の確認のため、今のパスワードを確かめる（アカウントの削除と同じ形のエラー）
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "currentPassword", "今のパスワードが正しくありません。");
        }
        // 今と同じパスワードには変えられない（今のパスワードは、上で一致を確かめたので、文字を比べるだけでよい）
        if (newPassword.equals(currentPassword)) {
            throw ApiException.validation(
                    List.of(new FieldErrorItem("newPassword", "今のパスワードと違うパスワードを入力してください。")));
        }

        // 変えた日時は、セッションを作った日時と同じ時計・同じ細かさ（ミリ秒）で決める（理由は PasswordChangeClock）
        Instant changedAt = PasswordChangeClock.now();
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword), changedAt);
        // まだ使っていない、パスワードの再設定のリンクは、使えなくする（古いリンクで、パスワードを戻されないように）
        passwordResetTokenMapper.invalidateUnusedByUserId(userId);
        log.atInfo().addKeyValue("event", "password_changed").addKeyValue("userId", userId).log("パスワードを変更しました");
    }

    /**
     * 【何のため】アカウントを削除する（要件定義書 5.5）。
     * 途中で条件に合わなければ、エラーを投げて、それまでの変更をすべて取り消す（@Transactional）。
     */
    @Transactional
    public void delete(long userId, String password) {
        // ユーザーの行をロックして、同じアカウントの削除が同時に行われないようにする
        User user = requireActive(userMapper.findByIdForUpdate(userId));

        // 本人の確認のため、パスワードを確かめる
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "password", "パスワードが正しくありません。");
        }

        // ① アーカイブしていない、オーナーをしているコミュニティがあれば、削除できない
        if (accountMapper.countActiveOwnedCommunities(userId) > 0) {
            throw ApiException.conflict("OWNS_ACTIVE_COMMUNITIES",
                    "オーナーをしているコミュニティがあります。先にコミュニティをアーカイブしてください。");
        }
        // ② アーカイブ済みでも、開催前のイベントが残っていれば、削除できない
        if (accountMapper.existsUpcomingEventInOwnedCommunities(userId)) {
            throw ApiException.conflict("OWNED_COMMUNITY_HAS_UPCOMING_EVENTS",
                    "オーナーをしているコミュニティに、開催前のイベントが残っています。イベントが終わってから削除してください。");
        }

        // 参加しているすべてのコミュニティから退会する（オーナーの行は残す）
        accountMapper.deleteMembershipsExceptOwner(userId);
        // 申請中の参加申請は、却下にする（削除したユーザーが、後から承認されてメンバーにならないように）
        accountMapper.rejectPendingJoinRequests(userId);
        // 開催前のイベントへの申込みを、すべて取り消す（繰り上げとお知らせも、ルールどおりに行われる）
        applicationCancelService.cancelUpcomingApplications(userId, userId, null);
        // パスワードの再設定のトークンを消す（削除したアカウントのリンクが、残らないように）
        passwordResetTokenMapper.deleteByUserId(userId);
        // 個人情報を消し、行だけを残す（過去の申込みの記録に「退会したユーザー」と表示するため）
        userMapper.markDeleted(userId);
        log.atInfo().addKeyValue("event", "account_deleted").addKeyValue("userId", userId).log("アカウントを削除しました");
    }

    // ユーザーがいない、または削除済みなら、ログインしていないものとして 401 を返す
    private static User requireActive(User user) {
        if (user == null || user.deletedAt() != null) {
            throw ApiException.unauthenticated();
        }
        return user;
    }
}
