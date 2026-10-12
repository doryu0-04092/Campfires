package com.campfires.common.security;

import java.time.Instant;

/**
 * 【役割】「パスワードを変えた日時」を決めるための時計。
 * 【なぜ必要か】パスワードを変えたら、変えた日時より前に作られたセッションを止める（ActiveUserFilter）。
 * 操作した本人は、変えた直後に新しいセッションを作り直す（LoginSession.restart）ので、
 * 「新しいセッションを作った日時 ≧ 変えた日時」に、必ずなっていなければならない。そうでないと、本人まで追い出されてしまう。
 *
 * ここで気をつけること：
 * - データベースの now() は使わない。データベースの時計とアプリの時計は少しずれることがあり、細かさもマイクロ秒で違う
 * - Instant.now() も使わない。Windows などでは、Instant.now() は細かい時計、System.currentTimeMillis() は
 *   数ミリ秒ごとにしか進まない粗い時計で、Instant.now() のほうが数ミリ秒先に進んでいることがある。
 *   開発用のセッション（Tomcat）は、作った日時を System.currentTimeMillis() で決めるので、
 *   変えた日時を Instant.now() にすると、直後に作ったセッションのほうが「前」になることがある
 * - そこで、System.currentTimeMillis()（粗いほうの時計。ミリ秒の単位）を使う。
 *   本番のセッション（Spring Session JDBC）は Instant.now()（細かいほう。粗いほうより前になることはない）で作るので、
 *   どちらの場合も「新しいセッションを作った日時 ≧ 変えた日時」になる
 */
public final class PasswordChangeClock {

    private PasswordChangeClock() {
    }

    /**
     * 【何のため】今の日時を、ミリ秒の単位で返す（パスワードを変えた日時として、データベースに保存する）。
     */
    public static Instant now() {
        return Instant.ofEpochMilli(System.currentTimeMillis());
    }
}
