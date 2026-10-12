package com.campfires.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.campfires.account.User;

/**
 * 【役割】開発用の「リンクの届け方」。パスワードの再設定のリンクを、メールの代わりにログ（画面の黒い窓）に書く。
 * 【なぜ必要か】メールを送る仕組みがなくても、手元で「リンクを開いて、パスワードを決め直す」までを試せるようにするため。
 *
 * 注意：リンクには、パスワードを変えられる秘密の値（トークン）が入っている。
 * ログを見られる人なら、だれでも他人のパスワードを変えられてしまうので、本番では絶対に使わない。
 * @Profile("dev") を付けたので、dev のプロファイル（./mvnw spring-boot:run）のときだけ使われる。
 */
@Component
@Profile("dev")
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    /**
     * 【何のため】再設定のリンクを、ログに書く（開発中に、そのリンクをブラウザで開いて試す）。
     */
    @Override
    public void send(User user, String resetUrl) {
        log.atWarn().addKeyValue("event", "password_reset_link_dev").addKeyValue("userId", user.id())
                .log("【開発用】パスワードの再設定のリンク（メールの代わりに表示しています）: {}", resetUrl);
    }
}
