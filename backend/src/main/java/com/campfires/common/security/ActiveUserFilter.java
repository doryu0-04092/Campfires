package com.campfires.common.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import com.campfires.account.User;
import com.campfires.account.UserMapper;
import com.campfires.common.observability.AccessLogFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 【役割】ログインしている人のアカウントが、削除されていないか・パスワードを変えた後に残った古いログインではないかを、
 * API の呼び出しのたびに確かめるフィルター。
 * 【なぜ必要か】アカウントを削除すると、削除の操作をした端末のセッションは消える。
 * しかし、ほかの端末（スマートフォンなど）でログインしていたセッションは残る。
 * そのセッションで操作を続けられないように、削除済みなら、セッションを消して「ログインしていない人」として扱う。
 * その後、ログインが必要な API なら、ふつうと同じように 401 になり、ログインなしで見られる API なら、そのまま見られる。
 *
 * パスワードを変えた（変更・再設定）ときも同じ。パスワードを盗まれて、ほかの端末でログインされていた場合に、
 * パスワードを変えれば、その端末を追い出せるように、変えた日時（users.password_changed_at）より前に作られたセッションを止める。
 * セッションに保存している LoginUser の形は変えない（本番では、セッションをデータベースに保存しているので、
 * 形を変えると、今ログインしている人のセッションが読めなくなるため）。代わりに、セッションを作った日時と比べる。
 * どちらの日時も、アプリの時計のミリ秒なので、同じ時計で比べられる（PasswordChangeClock。セッションを作った日時は、本番ではデータベースに保存される）。
 *
 * フィルターは「API の処理の前に、必ず通る関所」。OncePerRequestFilter は「1回の呼び出しで1度だけ通る」フィルター。
 * Spring に部品として登録すると、セキュリティの外でも自動でもう1度登録されてしまうため、
 * @Component は付けず、SecurityConfig の中で new して使う。
 */
public class ActiveUserFilter extends OncePerRequestFilter {

    // ログ（記録）を書くための道具。データベースのエラーの詳しい内容は、返事ではなくログに残す
    private static final Logger log = LoggerFactory.getLogger(ActiveUserFilter.class);

    private final UserMapper userMapper;
    private final ProblemResponseWriter problemWriter;

    public ActiveUserFilter(UserMapper userMapper, ProblemResponseWriter problemWriter) {
        this.userMapper = userMapper;
        this.problemWriter = problemWriter;
    }

    /**
     * 【何のため】ログインしている人が削除済み、またはパスワードを変える前のセッションなら、ログアウトさせる。どちらの場合も、そのまま先へ進める。
     * データベースにつながらないときは、ほかの API と同じ形のエラー（500、INTERNAL_ERROR）を返して、先へは進めない。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // instanceof LoginUser loginUser は「LoginUser なら、loginUser という名前で使う」という書き方
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            User user;
            try {
                user = userMapper.findById(loginUser.id());
            } catch (DataAccessException e) {
                // フィルターは Controller より前で動くので、ここで起きたエラーは GlobalExceptionHandler では受け止められず、
                // ほかの API と違う形のエラーになっていた（DatabaseOutageTest で発見）。
                // ここで直接、ほかの API と同じ形の返事を書く。内部の情報（SQL など）はログにだけ残す
                log.error("想定していないエラーが起きました", e);
                problemWriter.write(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "サーバーでエラーが起きました。時間をおいて、もう一度お試しください。");
                return;
            }
            if (user == null || user.deletedAt() != null) {
                logout(request, response, authentication);
            } else if (isCreatedBeforePasswordChange(request, user)) {
                // パスワードを変える前のセッション：ログアウトさせる（だれのセッションを止めたかは、ログに残す）
                log.atInfo().addKeyValue("event", "session_revoked").addKeyValue("userId", user.id())
                        .log("パスワードの変更より前のセッションを止めました");
                logout(request, response, authentication);
            } else {
                // アクセスログ（AccessLogFilter）に、だれの操作かを残すため、ログインしている人の番号を渡す
                request.setAttribute(AccessLogFilter.USER_ID_ATTRIBUTE, loginUser.id());
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 【何のため】今のセッションが、最後にパスワードを変えた日時より前に作られたものかどうかを返す。
     * パスワードを一度も変えていない、またはセッションがないときは false。
     * getCreationTime() は、セッションを作った日時（1970年1月1日からのミリ秒）。
     */
    private static boolean isCreatedBeforePasswordChange(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(false);
        return user.passwordChangedAt() != null && session != null
                && session.getCreationTime() < user.passwordChangedAt().toEpochMilli();
    }

    // セッションを消し、ログインの情報も消して、ブラウザの JSESSIONID の Cookie も消す
    private static void logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
    }
}
