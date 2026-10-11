package com.campfires.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.campfires.account.UserMapper;

/**
 * 【役割】ログイン、権限、CSRF の対策など、セキュリティの設定をまとめたクラス（Spring Security）。
 * 【なぜ必要か】「どの API がログインなしで呼べるか」「CSRF トークンをどう確かめるか」を1か所で決めるため。
 * ルールは docs/api/README.md の 1.6（ログイン）、1.7（CSRF）に合わせている。
 *
 * ここで決めるのは「ログインしているかどうか」まで。
 * 「オーナーだけ」のような細かい権限は、各 Service（業務処理）でチェックする。
 *
 * @Configuration は「設定を書いたクラス」、@Bean は「Spring に部品として登録するメソッド」という印。
 */
@Configuration
public class SecurityConfig {

    /**
     * 【何のため】セキュリティのルールを組み立てる。
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponseWriter problemWriter, UserMapper userMapper)
            throws Exception {
        http
                // ---------- CSRF の対策（docs/api/README.md 1.7） ----------
                // spa() は、画面と API を分けたアプリ向けの設定。
                // XSRF-TOKEN という Cookie で合言葉を渡し、画面は X-XSRF-TOKEN ヘッダーに入れて送り返す
                .csrf(csrf -> csrf.spa())

                // ---------- ログインなしで呼べる API と、ログインが必要な API ----------
                .authorizeHttpRequests(auth -> auth
                        // 登録、ログイン、ログアウト、CSRF トークンは、ログインしていなくても呼べる
                        .requestMatchers("/api/auth/**").permitAll()
                        // 死活監視（ロードバランサーが呼ぶ）は、ログインしていなくても呼べる
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // 一覧と詳細の取得（GET）は、ログインしていなくても見られる（要件定義書 3章）。
                        // 「*」は URL の1つの区切り（例：番号）に当てはまる
                        .requestMatchers(HttpMethod.GET,
                                "/api/events",
                                "/api/communities",
                                "/api/communities/*",
                                "/api/communities/*/events",
                                "/api/communities/*/events/*").permitAll()
                        // それ以外の API は、すべてログインが必要
                        .requestMatchers("/api/**").authenticated()
                        // API 以外（画面のファイル：/、/assets/〜、/communities/1 など）は、だれでも読める。
                        // 画面を読んだあと、画面が API を呼ぶときに、上の決まりでログインを確かめる
                        .anyRequest().permitAll())

                // ---------- 画面を守る HTTP ヘッダー（Issue #47） ----------
                .headers(headers -> headers
                        // CSP（Content Security Policy）：読み込んでよいものを、このサイト（'self'）のものに限る。
                        // もし画面にプログラムを埋め込まれても（XSS）、外のサイトから読み込んだり、外へ送ったりできないようにする。
                        // style-src の 'unsafe-inline' は、React の style 属性（style={{ … }}）を使っているため
                        .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                                "default-src 'self'",
                                "script-src 'self'",
                                "style-src 'self' 'unsafe-inline'",
                                "img-src 'self' data:",
                                "connect-src 'self'",
                                "object-src 'none'",
                                "base-uri 'self'",
                                "form-action 'self'",
                                "frame-ancestors 'none'")))
                        // 別のサイトへ移動するとき、こちらの URL の細かい部分（パス）を送らない
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // カメラ、マイク、位置情報などのブラウザの機能を、このサイトでは使わせない
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
                        // ヘッダーを、処理の前に書く（eagerly）。ふだんは、返事を送り出す瞬間に書くが、HEAD のリクエスト
                        // （中身なしで、ヘッダーだけを返す）では、その瞬間を通らずに送り出され、ヘッダーが付かなかった（E2E で発見）。
                        // addObjectPostProcessor は「Spring Security が作った部品（ここではヘッダーを書くフィルター）の設定を、あとから変える」書き方
                        .addObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                            @Override
                            public <O extends HeaderWriterFilter> O postProcess(O filter) {
                                filter.setShouldWriteHeadersEagerly(true);
                                return filter;
                            }
                        }))
                // HSTS（「このサイトは HTTPS だけで開く」とブラウザに覚えさせる）は、Spring Security が
                // HTTPS の通信のときに自動で付ける（本番では、ロードバランサーの HTTPS を、server.forward-headers-strategy で正しく扱う）

                // ---------- エラーの返し方 ----------
                .exceptionHandling(handling -> handling
                        // ログインしていないときは 401（docs/api/README.md 1.4）
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "ログインしてください。"))
                        // CSRF トークンが正しくないときは 403
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof CsrfException) {
                                problemWriter.write(response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID",
                                        "画面を読み込み直してから、もう一度お試しください。");
                            } else {
                                problemWriter.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "この操作をする権限がありません。");
                            }
                        }))

                // ---------- ログアウト（POST /api/auth/logout） ----------
                // セッションを消し、セッションの Cookie も消して、204（返す中身なし）を返す。
                // ログインしていなくても成功を返す（docs/api/openapi.yaml の logout）
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))

                // ---------- 削除済みのアカウントのセッションを止める ----------
                // ログインのチェック（AuthorizationFilter）の前に、アカウントが削除されていないかを確かめる。
                // 削除済みならログアウトさせるので、その後のログインのチェックで「ログインしていない人」として扱われる
                .addFilterBefore(new ActiveUserFilter(userMapper), AuthorizationFilter.class)

                // ログインの画面（フォーム）や、ブラウザの認証の窓は使わない（画面は React で作っているため）
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * 【何のため】パスワードを BCrypt で変換するための道具を、部品として登録する（非機能要件 3章）。
     * 変換した値は元に戻せず、ログインのときは「同じように変換して一致するか」で確かめる。
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 【何のため】ログイン状態を、セッションに保存するための道具を、部品として登録する。
     * ログインの API で、ログインに成功したときに使う。
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
