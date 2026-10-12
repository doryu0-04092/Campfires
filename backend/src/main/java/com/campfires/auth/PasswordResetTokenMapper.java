package com.campfires.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】パスワードの再設定のトークン（password_reset_tokens テーブル）の SQL の窓口。
 * 【なぜ必要か】パスワードを忘れた人に送るリンクの「使い捨ての合言葉（トークン）」を、保存・確認・無効化するため。
 * トークンそのものは保存せず、SHA-256 で変換した値（tokenHash）だけを扱う。
 * SQL の本文は src/main/resources/mapper/auth/PasswordResetTokenMapper.xml。
 */
@Mapper
public interface PasswordResetTokenMapper {

    /**
     * 【何のため】トークンを保存する。有効期限は「データベースの今の日時 + validMinutes 分」。
     * @param userId       だれのパスワードを再設定するためのトークンか
     * @param tokenHash    トークンを SHA-256 で変換した値（16進数の64文字）
     * @param validMinutes 有効な分数（30分）
     */
    void insert(@Param("userId") long userId, @Param("tokenHash") String tokenHash,
            @Param("validMinutes") int validMinutes);

    /**
     * 【何のため】まだ使える（未使用・期限内・アカウントが削除されていない）トークンを探す（ロックはしない）。
     * 見つからなければ null。だれのトークンかを先に知り、ユーザーの行から順にロックするために使う。
     */
    PasswordResetToken findUsable(@Param("tokenHash") String tokenHash);

    /**
     * 【何のため】まだ使える（未使用・期限内・アカウントが削除されていない）トークンを探し、行をロックする。
     * 見つからなければ null（知らない・期限切れ・使用済みを区別しない）。
     * ロックするので、同じトークンが同時に2回使われても、2回目は「使用済み」として断られる。
     */
    PasswordResetToken findUsableForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * 【何のため】そのユーザーの、まだ使っていないトークンを、すべて使えなくする（used_at に今の日時を入れる）。
     * 新しいトークンを作るとき、パスワードを変えたとき、再設定に使ったときに呼ぶ
     * （古いリンクが、メールの受信箱などに残っていても、使えないようにするため）。
     */
    void invalidateUnusedByUserId(@Param("userId") long userId);

    /**
     * 【何のため】そのユーザーのトークンを、すべて消す（アカウントの削除のとき）。
     */
    void deleteByUserId(@Param("userId") long userId);
}
