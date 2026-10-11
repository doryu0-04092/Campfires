package com.campfires.common.observability;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 【役割】API の呼び出しを1回ごとに、1行のログ（アクセスログ）に残すフィルター。
 * 【なぜ必要か】本番で「いつ・だれが・どの API を呼び・結果はどうで・何ミリ秒かかったか」を、あとから調べられるようにするため。
 * エラーが増えていないか、遅くなっていないかの数字（メトリクス）も、このログから CloudWatch で作る（infra/monitoring.tf）。
 *
 * 残す項目：method（GET など）、path（URL のパス）、status（結果の番号）、durationMs（かかった時間）、
 * userId（ログインしている人の番号。ログインしていなければ出さない）、clientIp（利用者の IP アドレス）。
 * 残さないもの：送られてきた中身（パスワードなど）と、URL の ? より後ろ（検索の言葉など）。個人の情報をログに広げないため。
 * トレースの番号（trace_id）は、トレースの部品（ADOT Java エージェント）が、ログに自動で足す（docs/observability.md）。
 *
 * @Order(Ordered.HIGHEST_PRECEDENCE) で、ほかのすべてのフィルターより外側で動く。
 * ほかのフィルターが断った通信（413、429 など）も、最後の結果をログに残せる。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    /** ログインしている人の番号を、ほかのフィルターから受け取るための、リクエストの属性の名前（ActiveUserFilter が入れる） */
    public static final String USER_ID_ATTRIBUTE = AccessLogFilter.class.getName() + ".userId";

    // ログの名前を「campfires.access」にして、ほかのログと分けて探せるようにする
    private static final Logger log = LoggerFactory.getLogger("campfires.access");

    /**
     * 【何のため】API の処理の前後で時間を測り、処理が終わったあとに、結果を1行のログに書く。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // System.nanoTime() は、時間を測るための時計（ナノ秒。時刻ではなく、2つの差だけに意味がある）
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            // 処理の途中で例外が外まで出てきたときは、サーバーのエラー（500）として記録し、例外はそのまま投げ直す
            failed = true;
            throw e;
        } finally {
            // finally は「成功しても、例外が出ても、必ず行う」処理
            int status = failed ? 500 : response.getStatus();
            if (shouldLog(request.getRequestURI(), status)) {
                write(request, status, (System.nanoTime() - start) / 1_000_000);
            }
        }
    }

    /**
     * 【何のため】ログに残すかを決める。API（/api/ で始まる）だけを残す。画面のファイル（HTML、JavaScript）は、数が多く役に立たないので残さない。
     * 死活の確認（/api/health）は、ロードバランサーと Docker が30秒ごとに呼ぶので、うまくいったときは残さない。
     */
    static boolean shouldLog(String path, int status) {
        if (!path.startsWith("/api/")) {
            return false;
        }
        return !(path.equals("/api/health") && status == 200);
    }

    /**
     * 【何のため】1行のログを書く。atInfo().addKeyValue(名前, 値) は、項目に名前を付けて書く書き方（SLF4J の書き方）。
     * 本番では、ログを JSON で出すので、名前を付けた項目が JSON の項目になり、CloudWatch で条件を付けて探せる。
     * 結果が 500 番台なら WARN（注意）、それ以外は INFO（ふつう）で書く。
     */
    private void write(HttpServletRequest request, int status, long durationMs) {
        // 三項演算子「条件 ? A : B」は「条件が正しければ A、違えば B」
        var builder = status >= 500 ? log.atWarn() : log.atInfo();
        builder = builder
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("clientIp", request.getRemoteAddr());
        if (request.getAttribute(USER_ID_ATTRIBUTE) instanceof Long userId) {
            builder = builder.addKeyValue("userId", userId);
        }
        // {} の部分に、後ろの値が順番に入る
        builder.log("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, durationMs);
    }
}
