package com.campfires.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 【役割】本番で、ログインの状態（セッション）の Cookie の決まりを設定するクラス（Issue #47）。
 * 【なぜ必要か】本番では、セッションをデータベースに置く仕組み（Spring Session JDBC）を使う。
 * この仕組みは、application.properties の server.servlet.session.cookie.〜 の設定を使わず、
 * そのままだと、Cookie に HttpOnly と Secure が付かない（テストで分かった）。
 * そこで、開発中と同じ決まり（docs/api/README.md 1.6）を、ここで直接設定する。
 *
 * @Profile("prod") を付けたので、本番の設定（prod のプロファイル）のときだけ使う。
 */
@Configuration
@Profile("prod")
public class SessionCookieConfig {

    /**
     * 【何のため】セッションの Cookie の決まりを作る。
     */
    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        // Cookie の名前は、開発中と同じにする（ログアウトのときに消す Cookie の名前をそろえるため）
        serializer.setCookieName("JSESSIONID");
        // 画面のプログラム（JavaScript）から読めないようにする
        serializer.setUseHttpOnlyCookie(true);
        // 暗号化された通信（HTTPS）のときだけ送る
        serializer.setUseSecureCookie(true);
        // 別のサイトから来たデータを変える操作には、Cookie を送らない（CSRF の対策）
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        return serializer;
    }
}
