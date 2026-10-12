package com.campfires.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.account.User;
import com.campfires.account.UserMapper;
import com.campfires.common.security.PasswordChangeClock;
import com.campfires.common.web.ApiException;

/**
 * 【役割】パスワードの再設定（F-25。パスワードを忘れた人が、メールのリンクから、パスワードを決め直す）の、業務の処理をするクラス。
 * 【なぜ必要か】「再設定の依頼」と「新しいパスワードを決める」の2つの段階のルール（要件定義書 5.13）を、1か所にまとめるため。
 *
 * 仕組み：
 * ① 依頼（requestReset）：推測できないトークン（32バイトの乱数）を作り、SHA-256 で変換した値だけを保存して、
 *    トークン付きのリンク（〜/password/reset#token=…）を、届ける係（PasswordResetNotifier）に渡す。有効期限は30分。
 * ② 再設定（resetPassword）：リンクのトークンを同じように変換して探し、使えるものなら、パスワードを変える。
 *    トークンは1回だけ使える。変えた後は、ほかの端末のログイン（セッション）を、すべて使えなくする。
 *
 * メールを送る仕組みはまだないので、本番では、この機能を止めている（campfires.password-reset.enabled=false）。
 * 止めているときは、どちらの API も 503（PASSWORD_RESET_UNAVAILABLE）を返す。
 */
@Service
public class PasswordResetService {

    // 再設定の出来事を、ログに残す（不正な再設定の試みを、あとから調べられるようにするため）。
    // メールアドレス、トークン、リンク、パスワードは、ログに出さない
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    // トークンの有効期限（分）
    static final int TOKEN_VALID_MINUTES = 30;
    // トークンの長さ（バイト）。32バイト＝256ビットの乱数なので、総当たりで当てることはできない
    static final int TOKEN_BYTES = 32;

    private final UserMapper userMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final LoginFailureMapper loginFailureMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier notifier;
    // この機能を使うかどうか（application.properties の campfires.password-reset.enabled）
    private final boolean enabled;
    // リンクの頭の部分（例：https://campfires.example.com）。リクエストの Host ヘッダーからは作らない
    // （攻撃者が Host を書き換えて、攻撃者のサイトへのリンクを本人に送らせる「Host ヘッダー攻撃」を防ぐため）
    private final String appBaseUrl;
    // 推測できない乱数を作る道具（暗号に使える強さの乱数）
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 【何のため】必要な部品と設定を受け取る。@Value("${名前}") は「設定ファイルの値を入れる」という印。
     */
    public PasswordResetService(UserMapper userMapper, PasswordResetTokenMapper tokenMapper,
            LoginFailureMapper loginFailureMapper, PasswordEncoder passwordEncoder, PasswordResetNotifier notifier,
            @Value("${campfires.password-reset.enabled}") boolean enabled,
            @Value("${campfires.app-base-url}") String appBaseUrl) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.loginFailureMapper = loginFailureMapper;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.enabled = enabled;
        // 最後の「/」は取り除いておく（つなげたときに「//」にならないように）
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    /**
     * 【何のため】この機能を使えるかどうかを返す（画面が「パスワードを忘れた場合」のリンクを出すかを決めるため）。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 【何のため】パスワードの再設定を依頼する。登録されている（削除していない）メールアドレスなら、
     * トークンを作り、再設定のリンクを届ける係に渡す。
     *
     * 登録されているかどうかは、返事でも、かかる時間でも分からないようにする（メールアドレスを探られないように）。
     * そのため、登録されていなくても、トークンを作る・変換する作業は同じように行い、エラーにもしない。
     */
    @Transactional
    public void requestReset(String email) {
        requireEnabled();
        // 登録の有無にかかわらず、トークンを作って変換する（かかる時間の差を小さくするため）
        String token = newToken();
        String tokenHash = sha256Hex(token);

        // アカウントを削除したユーザーは、メールアドレスが空になっているので見つからない
        User user = userMapper.findActiveByEmail(email);
        if (user == null) {
            // 登録されていないメールアドレスへの依頼も記録する（メールアドレスを探る攻撃に気づくため）。メールアドレスは書かない
            log.atInfo().addKeyValue("event", "password_reset_requested").log("パスワードの再設定が依頼されました（登録なし）");
            return;
        }

        // 前に作った、まだ使っていないトークンは、使えなくする（使えるリンクは、いつも最新の1つだけ）
        tokenMapper.invalidateUnusedByUserId(user.id());
        tokenMapper.insert(user.id(), tokenHash, TOKEN_VALID_MINUTES);
        try {
            // トークンは「#」の後ろ（フラグメント）に入れる。「#」より後ろは、ブラウザがサーバーへ送らないので、
            // 画面を開いたときに、サーバーのアクセスログ・トレース・ロードバランサーの記録に残らない（Referer にも入らない）
            notifier.send(user, appBaseUrl + "/password/reset#token=" + token);
        } catch (RuntimeException e) {
            // 届けるのに失敗しても、返事は同じ（202）にする。エラーを返すと「登録されている」と分かってしまうため。
            // 失敗はログに残し、利用者には、もう一度依頼してもらう
            log.atError().addKeyValue("event", "password_reset_delivery_failed").addKeyValue("userId", user.id())
                    .setCause(e).log("パスワードの再設定のリンクを届けられませんでした");
        }
        log.atInfo().addKeyValue("event", "password_reset_requested").addKeyValue("userId", user.id())
                .log("パスワードの再設定が依頼されました");
    }

    /**
     * 【何のため】リンクのトークンを確かめて、パスワードを新しくする。
     * 知らない・期限切れ・使用済みのトークンは、どれも同じエラー（400、INVALID_RESET_TOKEN）にする。
     *
     * 1つのトランザクション（@Transactional）の中で、パスワードの変更、トークンを使用済みにする、
     * ログインのロックを解く、をまとめて行う（途中で失敗したら、すべて取り消す）。
     * 自動ではログインさせない（リンクを盗まれた場合に、そのままログインまでされないように。ログインの画面で改めてログインしてもらう）。
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        requireEnabled();
        String tokenHash = sha256Hex(token);

        // ① まず、ロックせずにトークンを探して、だれのトークンかを知る
        PasswordResetToken found = tokenMapper.findUsable(tokenHash);
        if (found == null) {
            throw invalidToken();
        }
        // ② ユーザーの行 → トークンの行、の順にロックする。パスワードの変更・アカウントの削除も
        // 「ユーザー → トークン」の順なので、順番がそろい、お互いに待ち合って止まる（デッドロック）ことがない
        User user = userMapper.findByIdForUpdate(found.userId());
        PasswordResetToken locked = tokenMapper.findUsableForUpdate(tokenHash);
        // ①と②の間に、同じトークンが使われた・アカウントが削除された場合は、ここで見つからない
        if (user == null || user.deletedAt() != null || locked == null) {
            throw invalidToken();
        }

        // 変えた日時は、セッションを作った日時と同じ時計・同じ細かさ（ミリ秒）で決める（理由は PasswordChangeClock）
        Instant changedAt = PasswordChangeClock.now();
        userMapper.updatePassword(user.id(), passwordEncoder.encode(newPassword), changedAt);
        // 使ったトークンを含め、このユーザーの、まだ使っていないトークンを、すべて使えなくする（1回だけ使える）
        tokenMapper.invalidateUnusedByUserId(user.id());
        // ログインの失敗の記録を消して、ロックを解く（本人がパスワードを決め直したので、すぐにログインできるように）
        loginFailureMapper.clear(user.email().toLowerCase(Locale.ROOT));
        log.atInfo().addKeyValue("event", "password_reset").addKeyValue("userId", user.id())
                .log("パスワードを再設定しました");
    }

    // 機能を止めているときは 503。メールを送る仕組みができるまで、本番では止めている
    private void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PASSWORD_RESET_UNAVAILABLE",
                    "現在、パスワードの再設定はご利用いただけません。お手数ですが、お問い合わせください。");
        }
    }

    // 知らない・期限切れ・使用済みを区別しない（どれなのかを、攻撃者に教えないため）
    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                "このリンクは使えません（有効期限が切れたか、すでに使われています）。もう一度、再設定を依頼してください。");
    }

    /**
     * 【何のため】推測できないトークンを作る。32バイトの乱数を、URL にそのまま入れられる文字（Base64URL）にする。
     * withoutPadding() は、最後の「=」を付けない指定（URL の中で、特別な意味を持つ文字を避けるため）。
     */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 【何のため】トークンを SHA-256 で変換し、16進数の64文字にする（データベースには、この値だけを保存する）。
     * トークンは十分に長い乱数なので、パスワードと違い、BCrypt のような遅い変換は要らない（SHA-256 で十分に戻せない）。
     * また、SHA-256 なら同じ値から同じ結果になるので、データベースで直接探せる。
     */
    static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は、どの Java にも必ずある決まりなので、ここには来ない
            throw new IllegalStateException(e);
        }
    }
}
