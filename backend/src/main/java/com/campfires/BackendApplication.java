package com.campfires;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 【役割】バックエンド（Spring Boot のアプリ）の入り口となるクラス。
 * 【なぜ必要か】このクラスの main メソッドを実行すると、アプリが起動し、API を受け付け始める。
 *
 * @SpringBootApplication は「この場所から下のパッケージ（com.campfires.〜）にあるクラスを、
 * Spring が自動で探して組み立てる」という印。Controller や Service は、これで見つけてもらえる。
 */
@SpringBootApplication
public class BackendApplication {

	/**
	 * 【何のため】アプリを起動する。Java のプログラムは、main メソッドから実行が始まる。
	 */
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
