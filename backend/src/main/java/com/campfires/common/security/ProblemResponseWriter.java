package com.campfires.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.campfires.common.web.Problems;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 【役割】Spring Security が処理を止めたとき（ログインしていない、CSRF トークンが違う）に、エラーの返事を書き込むクラス。
 * 【なぜ必要か】Spring Security のチェックは、Controller より前で行われるので、GlobalExceptionHandler では受け止められない。
 * ここで直接、Problem Details の形の JSON を書き込み、ほかの API と同じ形のエラーにそろえる。
 *
 * @Component は「Spring が自動で作って、ほかのクラスに渡してくれる部品」という印。
 */
@Component
public class ProblemResponseWriter {

    // データを JSON の文字に変える道具（Jackson）。Spring Boot が用意したものを受け取る
    private final JsonMapper jsonMapper;

    /**
     * 【何のため】必要な部品を受け取る。Spring が自動で JsonMapper を渡してくれる（コンストラクタインジェクション）。
     */
    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 【何のため】エラーの返事を書き込む。
     */
    public void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        ProblemDetail problem = Problems.of(status, code, detail, List.of());
        response.setStatus(status.value());
        // 中身がエラーの返事（Problem Details）であることを伝える
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), problem);
    }
}
