package com.campfires.common.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【役割】アプリが動いているかを確かめる API（GET /api/health）。死活監視（ヘルスチェック）に使う。
 * 【なぜ必要か】本番では、ロードバランサー（ALB）が定期的にこの API を呼び、
 * 正しく返事をしない箱には、利用者の通信を送らないようにする（Issue #47）。
 * データベースにつながらないときも「動いていない」と返し、壊れた箱に通信が行かないようにする。
 * ログインしていなくても呼べる（SecurityConfig で許可している）。返すのは状態だけで、内部の情報は返さない。
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 【何のため】データベースに簡単な SQL（SELECT 1）を送り、返事があれば 200、なければ 503 を返す。
     */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (RuntimeException e) {
            // 503：今は使えない、という意味のステータスコード
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "unavailable"));
        }
    }
}
