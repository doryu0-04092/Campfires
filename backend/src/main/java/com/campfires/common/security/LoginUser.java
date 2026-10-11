package com.campfires.common.security;

import java.io.Serializable;

/**
 * 【役割】ログインしている人を表すデータ。ログインに成功したときに作り、セッションに保存する。
 * 【なぜ必要か】API の処理の中で「今ログインしているのは誰か」を知るため。
 * Controller では、引数に「@AuthenticationPrincipal LoginUser loginUser」と書くと、受け取れる。
 *
 * ユーザーの番号だけを持つ。ニックネームなどは変わることがあるので、必要なときにデータベースから読む。
 * Serializable は「セッションに保存できる形（ファイルなどに書き出せる形）」という印。
 *
 * @param id ログインしているユーザーの番号
 */
public record LoginUser(long id) implements Serializable {
}
