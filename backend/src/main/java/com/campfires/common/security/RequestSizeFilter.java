package com.campfires.common.security;

import java.io.IOException;

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
 * 【役割】1回の通信で送れるデータの大きさに、上限を付けるフィルター（Issue #47）。
 * 【なぜ必要か】とても大きなデータを送り付けられると、サーバーのメモリを使い切って、止まってしまうことがある。
 * 上限を超える通信は、中身を読む前に断る（413）。上限は application.properties の campfires.request.max-body-bytes。
 *
 * 大きさの分からない送り方（Transfer-Encoding: chunked）は、上限を確かめられないので断る（411）。
 * この画面（ブラウザの fetch）は、いつも大きさを付けて送るので、困ることはない。
 *
 * @Order で、ほかのフィルター（ログインのチェックなど）より先に動くようにする。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final ProblemResponseWriter problemWriter;

    /**
     * 【何のため】上限の大きさを設定から受け取る。@Value("${名前}") は「設定の値を、この引数に入れる」という印。
     */
    public RequestSizeFilter(@Value("${campfires.request.max-body-bytes}") long maxBodyBytes,
            ProblemResponseWriter problemWriter) {
        this.maxBodyBytes = maxBodyBytes;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // getContentLengthLong は、送られてくるデータの大きさ（分からなければ -1）
        if (request.getContentLengthLong() > maxBodyBytes) {
            problemWriter.write(response, HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "送られてきたデータが大きすぎます。");
            return;
        }
        String transferEncoding = request.getHeader("Transfer-Encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
            problemWriter.write(response, HttpStatus.LENGTH_REQUIRED, "LENGTH_REQUIRED",
                    "送るデータの大きさ（Content-Length）を付けてください。");
            return;
        }
        chain.doFilter(request, response);
    }
}
