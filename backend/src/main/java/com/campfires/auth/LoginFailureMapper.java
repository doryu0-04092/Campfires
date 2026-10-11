package com.campfires.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】ログインの失敗の記録（login_failures テーブル）の SQL の窓口。
 * 【なぜ必要か】ログインの試行制限（5回続けて失敗したら15分ロック。非機能要件 3章）のため。
 * パスワードを総当たりで試す攻撃（ブルートフォース攻撃）を防ぐ。
 * SQL の本文は src/main/resources/mapper/auth/LoginFailureMapper.xml。
 */
@Mapper
public interface LoginFailureMapper {

    /**
     * 【何のため】メールアドレスのロックの状態を読む。記録がなければ null。
     * @param email 小文字にそろえたメールアドレス
     */
    LoginLock findLock(@Param("email") String email);

    /**
     * 【何のため】失敗を1回数える。決まった回数に達したら、決まった時間だけロックする。
     * ロックが解けた後の失敗は、1回目から数え直す。
     * @param email       小文字にそろえたメールアドレス
     * @param maxFailures ロックするまでの回数
     * @param lockMinutes ロックする分数
     */
    void recordFailure(@Param("email") String email, @Param("maxFailures") int maxFailures,
            @Param("lockMinutes") int lockMinutes);

    /**
     * 【何のため】ログインに成功したら、失敗の記録を消す。
     */
    void clear(@Param("email") String email);
}
