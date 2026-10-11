# backend（API）

Campfires の API です。Java 21、Spring Boot 4、MyBatis、PostgreSQL で作っています。API の仕様は [`docs/api`](../docs/api/README.md) にあり、このバックエンドはそのとおりに作ります。

## 必要なもの

| もの | 確かめ方 |
|---|---|
| Java 21 | `java -version` |
| Docker Desktop（起動しておく） | `docker info` |

Maven は、プロジェクトに入っている `mvnw`（Maven Wrapper）が自動でダウンロードするので、入れなくても動きます。

## 使い方

`backend` フォルダでターミナルを開き、次のコマンドを実行します。

| コマンド | 内容 |
|---|---|
| `./mvnw spring-boot:run` | アプリを起動する。`http://localhost:8080` で API を受け付ける。Docker の PostgreSQL も自動で起動する |
| `./mvnw test` | 自動テストを実行する。テスト用の PostgreSQL を Docker で起動し、終わったら片付ける |

Windows のコマンドプロンプトや PowerShell では、`./mvnw` の代わりに `.\mvnw.cmd` を使います。

起動を止めるときは、ターミナルで `Ctrl + C` を押します。データベースは動いたままになります（次に起動したとき、すぐ使えるように）。止めるときは `docker compose down` を実行します。

## フォルダの分け方

```
src/main/java/com/campfires/
├─ BackendApplication.java   アプリの入り口
├─ common/                   すべての機能で使う共通の仕組み
│   ├─ security/             ログイン、CSRF、権限（Spring Security の設定）
│   ├─ search/               検索のルール（単語の分け方、300件の上限）
│   └─ web/                  エラーの返し方（Problem Details）、一覧のページ分け
├─ auth/                     アカウント登録、ログイン、ログアウト、CSRF トークン
├─ account/                  自分のアカウント（情報の取得、ニックネームの変更、アカウントの削除）
├─ community/                コミュニティとメンバー（一覧・検索、作成・編集、アーカイブ、参加申請、運営スタッフ、除名、退会）
├─ event/                    イベントと申込み（申込みの取り消しの共通処理だけ作成済み。API は Issue #34 で作る）
└─ notification/             お知らせ（お知らせを作る SQL だけ作成済み。API は Issue #35 で作る）

src/main/resources/
├─ application.properties    設定
├─ db/migration/             テーブルの作成・変更の SQL（Flyway）
└─ mapper/                   MyBatis の SQL（XML）

src/test/java/com/campfires/ 自動テスト（本物の PostgreSQL で動かす）
```

機能ごとのフォルダには、次の3種類のクラスを置きます。

| クラス | 役割 | たとえ |
|---|---|---|
| `〜Controller` | API の受付。URL と、送られてきたデータを受け取り、Service を呼んで、返事を返す | 窓口 |
| `〜Service` | 業務処理。権限のチェックや、要件定義書の業務ルールを守る | 担当者 |
| `〜Mapper` | データベースの読み書き。SQL は `resources/mapper` の XML に書く | 書類棚 |

## ファイルの役割と、差分を見るときの視点

画面側（`frontend/README.md`）と同じく、読む深さの記号を使います（◎：毎回しっかり読む、○：変更されたときだけ読む、△：中身は読まない、×：見ない）。

| ファイル | 深さ | 見る視点 |
|---|:-:|---|
| `〜Service.java` | ◎ | 業務ルールが要件定義書どおりか。権限のチェックがあるか。まとめて成功・失敗させる処理に `@Transactional` が付いているか |
| `〜Controller.java` | ◎ | URL と返事の形が API 仕様書どおりか。送られてきたデータに `@Valid`（入力チェック）が付いているか |
| `resources/mapper/〜.xml` | ◎ | SQL が意図どおりか。入力の値は `#{}` で渡しているか（`${}` は SQL インジェクションの危険があるので使わない） |
| `db/migration/V〜.sql` | ◎ | テーブルの変更の内容。**一度 main に入ったファイルは書き換えない**（変更は新しい番号のファイルで行う） |
| `common/security/SecurityConfig.java` | ○ | ログインなしで呼べる API が増えていないか。CSRF の対策が外されていないか |
| `src/test/〜` | ○ | 正常な場合だけでなく、失敗する場合（権限なし、満席など）も確かめているか |
| `application.properties` | ○ | パスワードなどの秘密情報が直接書かれていないか。Cookie の設定が緩められていないか |
| `compose.yaml` | ○ | 開発用のデータベースの設定。開発用の値だけで、本番の値が書かれていないか |
| `pom.xml` | ○ | 使うライブラリが増えた・減ったか（画面側の `package.json` と同じ） |
| `mvnw`、`mvnw.cmd`、`.mvn/` | △ | Maven を動かすための自動生成のファイル。中身は読まない |
| `target/` | × | ビルドで作られるファイル（画面側の `dist` にあたる）。Git で管理しない |

## 開発用の値について

`compose.yaml` にあるデータベースのパスワード（`campfires-dev`）は、自分の PC の中だけで使う開発用の値です。本番の環境では、環境変数などで別の値を渡し、ファイルには書きません。
