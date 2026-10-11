package com.campfires.common.security;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 【役割】ログインとアカウント登録の回数を、IP アドレスごとに制限するフィルター（Issue #47）。
 * 【なぜ必要か】ログインの失敗は、メールアドレスごとに5回でロックしている（非機能要件 3章）。
 * しかし、たくさんのメールアドレスを順番に試す攻撃や、アカウントを大量に作る攻撃は、それでは防げない。
 * 同じ IP アドレスからの回数を、決まった時間の中で制限して、こうした攻撃を遅らせる。
 *
 * 数え方：時間を区切り（窓）、その区切りの中での回数を数える（固定ウィンドウ方式）。区切りが変われば、0 から数え直す。
 * 回数はアプリの中（メモリ）で数えるので、箱が2つ以上あると、箱ごとに数える（それでも、攻撃をかなり遅らせられる）。
 * 本番だけ使う（application-prod.properties の campfires.rate-limit.enabled=true）。
 * 利用者の本当の IP アドレスは、ロードバランサーが付けるヘッダーから、Tomcat が取り出す（server.forward-headers-strategy）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * 【何のため】制限する API ごとの決まり。
     * @param path        API の URL
     * @param maxRequests 区切りの中で、許す回数
     * @param windowSec   区切りの長さ（秒）
     */
    record Rule(String path, int maxRequests, long windowSec) {
    }

    // ログイン：10分間に 20回まで。アカウント登録：1時間に 10回まで（ふつうの使い方では、まず届かない回数）
    static final Rule LOGIN = new Rule("/api/auth/login", 20, 600);
    static final Rule SIGNUP = new Rule("/api/auth/signup", 10, 3600);

    // 回数制限で断ったことを、ログに残す（攻撃を受けていないかを、あとから調べるため）
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final boolean enabled;
    private final ProblemResponseWriter problemWriter;
    // 「決まり・IP アドレス・区切りの番号」→ 回数。ConcurrentHashMap は、同時に読み書きしても壊れない入れ物
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    // 今の時刻を返す道具（テストで時刻を進めたいときに、差し替えられるようにする）
    private Clock clock = Clock.systemUTC();

    public RateLimitFilter(@Value("${campfires.rate-limit.enabled}") boolean enabled,
            ProblemResponseWriter problemWriter) {
        this.enabled = enabled;
        this.problemWriter = problemWriter;
    }

    /**
     * 【何のため】テストのときだけ、時刻を差し替える。required = false で、ふだんは何もしない。
     */
    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = ruleFor(request);
        if (enabled && rule != null) {
            long nowSec = clock.millis() / 1000;
            long window = nowSec / rule.windowSec();
            String key = rule.path() + "|" + request.getRemoteAddr() + "|" + window;
            int count = counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
            if (count > rule.maxRequests()) {
                // あと何秒で、次の区切り（数え直し）になるか
                long retryAfter = (window + 1) * rule.windowSec() - nowSec;
                log.atWarn().addKeyValue("event", "rate_limited").addKeyValue("path", rule.path())
                        .addKeyValue("clientIp", request.getRemoteAddr()).log("回数制限を超えたので断りました");
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                problemWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "短い時間に、何度も試されています。しばらく待ってから、もう一度お試しください。");
                return;
            }
            removeOldCounters(window, rule);
        }
        chain.doFilter(request, response);
    }

    // 制限する API なら、その決まりを返す。データを送る POST だけを数える
    private static Rule ruleFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (LOGIN.path().equals(uri)) {
            return LOGIN;
        }
        if (SIGNUP.path().equals(uri)) {
            return SIGNUP;
        }
        return null;
    }

    // 終わった区切りの回数を消して、入れ物が大きくなり続けないようにする（入れ物が大きくなったときだけ行う）
    private void removeOldCounters(long currentWindow, Rule rule) {
        if (counters.size() < 10_000) {
            return;
        }
        String prefix = rule.path() + "|";
        counters.keySet().removeIf(key -> key.startsWith(prefix)
                && Long.parseLong(key.substring(key.lastIndexOf('|') + 1)) < currentWindow);
    }
}
