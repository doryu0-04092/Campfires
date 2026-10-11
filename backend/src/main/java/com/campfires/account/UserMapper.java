package com.campfires.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】users テーブルを読み書きするための窓口（MyBatis の Mapper）。
 * 【なぜ必要か】SQL を Java のメソッドとして呼べるようにするため。
 * SQL の本文は src/main/resources/mapper/account/UserMapper.xml に書いている（メソッドの名前で結び付く）。
 *
 * @Mapper は「MyBatis の Mapper」という印。中身（実装）は、MyBatis が自動で作ってくれる。
 * @Param("名前") を付けると、XML の SQL の中で #{名前} として値を使える。
 */
@Mapper
public interface UserMapper {

    /**
     * 【何のため】番号でユーザーを探す（削除したユーザーも含む）。見つからなければ null。
     */
    User findById(@Param("id") long id);

    /**
     * 【何のため】番号でユーザーを探し、同時に行をロックする（SELECT … FOR UPDATE）。
     * アカウントの削除のように、同じユーザーの行を同時に書き換えられたくない処理で使う。
     */
    User findByIdForUpdate(@Param("id") long id);

    /**
     * 【何のため】メールアドレスで、削除していないユーザーを探す（大文字と小文字は区別しない）。見つからなければ null。
     */
    User findActiveByEmail(@Param("email") String email);

    /**
     * 【何のため】ユーザーを登録し、登録した行を返す。
     * 同意した日時には、データベースの今の日時を入れる。
     * 同じメールアドレスがすでにあれば、データベースの制約で DuplicateKeyException が投げられる。
     */
    User insert(@Param("realName") String realName, @Param("nickname") String nickname,
            @Param("email") String email, @Param("passwordHash") String passwordHash,
            @Param("policiesVersion") String policiesVersion);

    /**
     * 【何のため】ニックネームを変える。
     */
    void updateNickname(@Param("id") long id, @Param("nickname") String nickname);

    /**
     * 【何のため】アカウントを削除した状態にする。行は残し、本名、ニックネーム、メールアドレス、パスワードを消す（要件定義書 5.5）。
     */
    void markDeleted(@Param("id") long id);
}
