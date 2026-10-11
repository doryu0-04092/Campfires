package com.campfires.account;

/**
 * 【役割】ほかのユーザーを表すときの形（docs/api/openapi.yaml の UserRef）。
 * 【なぜ必要か】ほかのユーザーには、ニックネームだけを返し、本名とメールアドレスは返さない（docs/api/README.md 1.9）。
 * 返す項目を、この形に限ることで、うっかり個人情報を返してしまうことを防ぐ。
 *
 * @param userId   ユーザーの番号
 * @param nickname ニックネーム。アカウントを削除した人は null（画面で「退会したユーザー」と表示する）
 */
public record UserRef(long userId, String nickname) {
}
