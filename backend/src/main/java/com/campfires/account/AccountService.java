package com.campfires.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.common.web.ApiException;
import com.campfires.event.ApplicationCancelService;

/**
 * 【役割】自分のアカウント（/api/me）の、業務の処理をするクラス。
 * 【なぜ必要か】情報の取得、ニックネームの変更、アカウントの削除（要件定義書 5.5、5.6）のルールを、1か所にまとめるため。
 */
@Service
public class AccountService {

    // アカウントの削除を、ログに残す（あとから、いつ削除されたかを調べられるようにするため）
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final ApplicationCancelService applicationCancelService;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserMapper userMapper, AccountMapper accountMapper,
            ApplicationCancelService applicationCancelService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.applicationCancelService = applicationCancelService;
        this.passwordEncoder = passwordEncoder;
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
