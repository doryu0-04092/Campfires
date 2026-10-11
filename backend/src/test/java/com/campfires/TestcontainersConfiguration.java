package com.campfires;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 【役割】自動テストのときに、Docker で本物の PostgreSQL を動かす設定。
 * 【なぜ必要か】テストを本物のデータベースで動かし、SQL や制約（重複の禁止など）まで確かめるため。
 * Testcontainers という道具が、テストの開始時にデータベースを起動し、終わったら片付ける。
 *
 * @TestConfiguration は「テストのときだけ使う設定」という印。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * 【何のため】テスト用の PostgreSQL を用意する。版は開発用（compose.yaml）と同じ 17 にそろえる。
	 * @ServiceConnection を付けると、Spring Boot がこのデータベースへのつなぎ先を自動で設定してくれる。
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
