package com.campfires.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;

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
                        // 一覧と詳細の取得（GET）は、ログインしていなくても見られる（要件定義書 3章）。
                        // 「*」は URL の1つの区切り（例：番号）に当てはまる
                        .requestMatchers(HttpMethod.GET,
                                "/api/events",
                                "/api/communities",
                                "/api/communities/*",
                                "/api/communities/*/events",
                                "/api/communities/*/events/*").permitAll()
                        // それ以外は、すべてログインが必要
                        .anyRequest().authenticated())

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
                // ログインのチェック（AuthorizationFilter）の後に、アカウントが削除されていないかを確かめる
                .addFilterAfter(new ActiveUserFilter(userMapper, problemWriter), AuthorizationFilter.class)

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
