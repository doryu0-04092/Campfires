package com.campfires.auth;

import com.campfires.account.User;

/**
 * 【役割】パスワードの再設定のリンクを、本人に届ける係の「決まり（インターフェース）」。
 * 【なぜ必要か】今は、メールを送る仕組みがない（要件定義書 4.3。メールの送信は今後の課題）。
 * 「リンクを届ける」部分だけを、この決まりの後ろに隠しておくと、メールの送信を始めるときは、
 * この決まりに合わせたクラス（実装）を1つ足すだけで済み、再設定の仕組み（PasswordResetService）は変えなくてよい。
 *
 * 今ある実装：
 * - LoggingPasswordResetNotifier：開発用（dev のプロファイル）。リンクをログに書くだけ。本番では絶対に使わない
 * - NoopPasswordResetNotifier：それ以外（自動テスト、本番）。何も送らず、「依頼があった」ことだけをログに残す
 *
 * メールの送信を始めるとき（例：Amazon SES を使う場合）：
 * ① この決まりに合わせた SesPasswordResetNotifier のようなクラスを作り、send の中でメールを送る
 * ② 本番だけで使うように、@Profile("prod") を付ける（NoopPasswordResetNotifier は、本番では使わないようにする）
 * ③ application-prod.properties の campfires.password-reset.enabled を true にする
 * メールの送信は時間がかかることがあるので、返事の速さで「登録されているか」を探られないように、
 * 送信は別の処理（キューや非同期の処理）に任せて、send はすぐに戻るようにするとよい。
 */
public interface PasswordResetNotifier {

    /**
     * 【何のため】パスワードの再設定のリンクを、本人に届ける。
     * @param user     再設定を依頼されたユーザー（メールアドレスの宛先に使う）
     * @param resetUrl 再設定の画面のリンク（使い捨てのトークン付き）。秘密の値なので、ログに書いてはいけない
     */
    void send(User user, String resetUrl);
}
