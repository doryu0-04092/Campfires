package com.campfires.common.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.campfires.common.observability.AccessLogFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 【役割】ログインした状態を始める・終わらせる処理をまとめたクラス。
 * 【なぜ必要か】ログインの API とアカウントの登録の API は、どちらも「ログインした状態」にする。
 * アカウントの削除の API は「ログアウトした状態」にする。同じ処理を1か所にまとめる。
 *
 * ログインした状態は、サーバーのセッション（利用者ごとの記憶の場所）に保存し、
 * ブラウザには、セッションの番号（JSESSIONID）だけを HttpOnly の Cookie で渡す（docs/api/README.md 1.6）。
 *
 * @Component は「Spring に部品として登録するクラス」という印。
 */
@Component
public class LoginSession {

    private final SecurityContextRepository securityContextRepository;

    public LoginSession(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * 【何のため】ユーザーを、ログインした状態にする。
     * ログインの前からセッションがあれば、セッションの番号を新しく作り直す（セッション固定攻撃の対策）。
     * セッション固定攻撃：攻撃者が用意したセッションの番号を使わせて、ログインした後に乗っ取る攻撃。
     */
    public void start(long userId, HttpServletRequest request, HttpServletResponse response) {
        // getSession(false) は「セッションがあれば返し、なければ作らずに null を返す」
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        // 「誰がログインしているか」の情報（Authentication）を作る。権限の一覧は使わないので空にする
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        // 今の処理の中でもログインした状態にし、次の API の呼び出しのために、セッションにも保存する
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // アクセスログ（AccessLogFilter）に、ログインした人の番号を残すため
        request.setAttribute(AccessLogFilter.USER_ID_ATTRIBUTE, userId);
    }

    /**
     * 【何のため】ログアウトした状態にする（セッションを消し、セッションの Cookie も消す）。
     * アカウントを削除したときに使う（ふつうのログアウトは SecurityConfig の logout の設定で行う）。
     */
    public void end(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // セッションを消し、ログインの情報も消す
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        // ブラウザの JSESSIONID の Cookie を消す（期限切れの Cookie を送り返す）
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
    }
}
