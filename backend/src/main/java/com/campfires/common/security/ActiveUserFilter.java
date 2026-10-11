package com.campfires.common.security;

import java.io.IOException;

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

/**
 * 【役割】ログインしている人のアカウントが、削除されていないかを、API の呼び出しのたびに確かめるフィルター。
 * 【なぜ必要か】アカウントを削除すると、削除の操作をした端末のセッションは消える。
 * しかし、ほかの端末（スマートフォンなど）でログインしていたセッションは残る。
 * そのセッションで操作を続けられないように、削除済みなら、セッションを消して「ログインしていない人」として扱う。
 * その後、ログインが必要な API なら、ふつうと同じように 401 になり、ログインなしで見られる API なら、そのまま見られる。
 *
 * フィルターは「API の処理の前に、必ず通る関所」。OncePerRequestFilter は「1回の呼び出しで1度だけ通る」フィルター。
 * Spring に部品として登録すると、セキュリティの外でも自動でもう1度登録されてしまうため、
 * @Component は付けず、SecurityConfig の中で new して使う。
 */
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;

    public ActiveUserFilter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 【何のため】ログインしている人が削除済みなら、ログアウトさせる。どちらの場合も、そのまま先へ進める。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // instanceof LoginUser loginUser は「LoginUser なら、loginUser という名前で使う」という書き方
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            User user = userMapper.findById(loginUser.id());
            if (user == null || user.deletedAt() != null) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
            } else {
                // アクセスログ（AccessLogFilter）に、だれの操作かを残すため、ログインしている人の番号を渡す
                request.setAttribute(AccessLogFilter.USER_ID_ATTRIBUTE, loginUser.id());
            }
        }
        chain.doFilter(request, response);
    }
}
