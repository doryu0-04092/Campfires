package com.campfires.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.campfires.account.User;

/**
 * 【役割】何も送らない「リンクの届け方」。dev 以外（自動テスト、本番）で使う。
 * 【なぜ必要か】メールを送る仕組みは、まだない（要件定義書 4.3）。それでも再設定の仕組みは動くように、
 * 「届ける係」の席には、何もしないものを置いておく。
 * 本番では、そもそも再設定の機能を止めている（application-prod.properties の campfires.password-reset.enabled=false）。
 *
 * ログには「依頼があった」ことと、ユーザーの番号だけを残す。リンクとトークンは、秘密の値なので書かない。
 * メールの送信を始めるときは、PasswordResetNotifier の説明のとおり、本物の実装を足して、こちらと入れ替える。
 * @Profile("!dev") は「dev のプロファイルではないとき」という意味。
 */
@Component
@Profile("!dev")
public class NoopPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoopPasswordResetNotifier.class);

    /**
     * 【何のため】何も送らず、依頼があったことだけをログに残す。
     */
    @Override
    public void send(User user, String resetUrl) {
        log.atInfo().addKeyValue("event", "password_reset_not_delivered").addKeyValue("userId", user.id())
                .log("パスワードの再設定が依頼されましたが、届ける仕組みがないため、送っていません");
    }
}
