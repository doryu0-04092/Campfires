package com.campfires;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 【役割】アプリ全体が起動できるかと、データベースのテーブルが作られるかを確かめるテスト。
 * 【なぜ必要か】設定の書き間違いや、マイグレーション（V1__create_tables.sql）の誤りに、最初に気づくため。
 *
 * @SpringBootTest は「アプリ全体を起動してテストする」という印。
 * @Import(TestcontainersConfiguration.class) で、Docker の本物の PostgreSQL を使う。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BackendApplicationTests {

	// SQL を直接実行するための道具。テストでテーブルの一覧を調べるのに使う
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * 【何のため】アプリが起動し、Flyway で全テーブルが作られていることを確かめる。
	 */
	@Test
	void アプリが起動し全テーブルが作られる() {
		// public という場所（スキーマ）にあるテーブルの名前を、名前の順に取り出す
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
				String.class);

		// containsExactlyInAnyOrder：順番は問わず、ちょうどこのテーブルだけがあること
		assertThat(tables).containsExactlyInAnyOrder(
				"applications", "communities", "events", "flyway_schema_history", "join_requests",
				"login_failures", "memberships", "notifications", "users");
	}

}
