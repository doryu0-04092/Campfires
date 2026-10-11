package com.campfires.auth;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.account.User;
import com.campfires.account.UserMapper;
import com.campfires.common.web.ApiException;

/**
 * 【役割】アカウントの登録とログインの、業務の処理をするクラス。
 * 【なぜ必要か】入力のチェックの後の「メールアドレスの重複の確認」「パスワードの確認」「ログインの試行制限」などの
 * ルールを、Controller（API の受け付け）から分けて、1か所にまとめるため。
 */
@Service
public class AuthService {

    // ログインの試行制限：5回続けて失敗したら、15分間ロックする（非機能要件 3章）
    static final int MAX_LOGIN_FAILURES = 5;
    static final int LOCK_MINUTES = 15;

    // 今のプライバシーポリシーと利用規約のバージョン（制定日）。文章を変えたら、ここも変える
    static final String POLICIES_VERSION = "2026-10-11";

    private final UserMapper userMapper;
    private final LoginFailureMapper loginFailureMapper;
    private final PasswordEncoder passwordEncoder;
    // 登録されていないメールアドレスでも、パスワードの確認と同じだけ時間をかけるための、ダミーの変換した値
    private final String dummyPasswordHash;

    public AuthService(UserMapper userMapper, LoginFailureMapper loginFailureMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.loginFailureMapper = loginFailureMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 【何のため】アカウントを登録する。パスワードは BCrypt で変換して保存する。
     * 同意した日時と、規約のバージョンも記録する（要件定義書 5.7）。
     */
    @Transactional
    public User signup(SignupRequest request) {
        // 先に確かめて、分かりやすいエラーを返す
        if (userMapper.findActiveByEmail(request.email()) != null) {
            throw emailAlreadyRegistered();
        }
        try {
            return userMapper.insert(request.realName(), request.nickname(), request.email(),
                    passwordEncoder.encode(request.password()), POLICIES_VERSION);
        } catch (DuplicateKeyException e) {
            // 確かめた直後に、ほかの人が同じメールアドレスで登録したとき（データベースの制約で止まる）
            throw emailAlreadyRegistered();
        }
    }

    /**
     * 【何のため】メールアドレスとパスワードを確かめて、ログインするユーザーを返す。
     * 失敗が続いたらロックする。どちらが違うかは教えない（登録されているメールアドレスを探られないようにするため）。
     *
     * @Transactional を付けていない：失敗したときにエラーを投げても、失敗の記録は取り消さずに残したいため。
     */
    public User login(LoginRequest request) {
        // メールアドレスは、大文字と小文字を区別せずに扱う。Locale.ROOT は「言語によらない変換」
        String key = request.email().toLowerCase(Locale.ROOT);

        // ロック中なら 429。あと何秒で試せるかを Retry-After のヘッダーで返す
        LoginLock lock = loginFailureMapper.findLock(key);
        if (lock != null && lock.locked()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                    "ログインに続けて失敗したため、しばらくログインできません。15分ほど待ってから、もう一度お試しください。",
                    List.of(), Map.of("Retry-After", String.valueOf(lock.retryAfterSeconds())));
        }

        // アカウントを削除したユーザーは、メールアドレスが空になっているので見つからない
        User user = userMapper.findActiveByEmail(key);
        // ユーザーがいないときも、ダミーの値でパスワードを確かめる（かかる時間の差で、登録の有無を探られないようにする）
        boolean matches = passwordEncoder.matches(request.password(),
                user != null ? user.passwordHash() : dummyPasswordHash);
        if (user == null || !matches) {
            loginFailureMapper.recordFailure(key, MAX_LOGIN_FAILURES, LOCK_MINUTES);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "メールアドレスまたはパスワードが正しくありません。");
        }

        // 成功したら、失敗の記録を消す
        loginFailureMapper.clear(key);
        return user;
    }

    private static ApiException emailAlreadyRegistered() {
        return ApiException.conflict("EMAIL_ALREADY_REGISTERED", "このメールアドレスは、すでに登録されています。");
    }
}
