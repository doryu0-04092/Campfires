# セキュリティの見直し

<!--
  【役割】Campfires のセキュリティの見直しの結果をまとめた文書（2026年10月）。
  【なぜ必要か】どんな攻撃を想定し、どう守っているか、見つけた問題をどう直したか、受け入れたリスクは何かを残し、
  あとから設定を変えるときや、ライブラリの版を上げるときに、守りが外れていないかを確かめられるようにするため。
-->

本番に公開する前に、画面・API・データベース・インフラと、そのつながりを見直しました。

## 1. 調べ方

| 対象 | 調べ方 |
|---|---|
| 画面のライブラリ（npm） | `npm audit`（本番に入る部品と、開発だけの部品の両方） |
| API のライブラリ（Java）、Java 本体、土台の OS | Trivy で、本番用の Docker イメージの中身を調べた（`trivy image`） |
| Dockerfile、Terraform の設定 | Trivy の設定チェック（`trivy config`） |
| アプリの作り | OWASP Top 10 の観点で、コードを読み、攻撃の形の通信を送って確かめた（自動テストと、Edge での確認） |
| AWS の構成 | AWS の公式の説明（ECS Express Mode、RDS、Secrets Manager、IAM の管理ポリシー）と照らし合わせた |

## 2. 見つけて直した問題

| # | 場所 | 問題 | 直し方 | 確かめ方 |
|---|---|---|---|---|
| 1 | Tomcat 11.0.24 | 重大な脆弱性3件（CVE-2026-65182 ほか。セキュリティの制限・DIGEST 認証・FORM 認証の回り避け）。このアプリはこれらの仕組みを使っていないが、版を上げる | 11.0.26 に上げた（pom.xml で版を上書き） | Trivy で 0件 |
| 2 | Jackson 3.1.5 / 2.21.5 | 高5件・中2件（大きな・深い JSON によるサービス停止など） | 3.1.7 / 2.21.7 に上げた（pom.xml で版を上書き） | Trivy で 0件 |
| 3 | 土台の OS（Ubuntu 26.04） | 中40件・低4件、Go の道具（pebble）に高3件など | 土台を Alpine Linux 版の Java 21 にし、作るときに OS のパッケージを最新にした | Trivy で 0件（OS もライブラリも） |
| 4 | 画面のファイルを返す仕組み（SpaConfig） | Spring のパストラバーサル（`../` でフォルダの外を読む）の確認を通らない書き方だった。Tomcat と Spring Security が先に防ぐので、実際には読まれなかった | Spring の確認（`PathResourceResolver.getResource`）を必ず通す書き方にした | 自動テスト（`../application.properties` を返さない） |
| 5 | データベースのパスワード | RDS に管理させると 7日ごとに自動で変わるが、ECS は箱の起動のときにしかパスワードを渡さないため、7日ほどでデータベースにつなげなくなる（AWS の説明で確認） | パスワードは Terraform で作り、自動では変えない。記録に残さない仕組み（ephemeral、write-only）を使う。変える手順を infra/README.md に書いた | `terraform validate` |
| 6 | データベースとの通信 | 暗号化はされるが、相手が本物の RDS かを確かめていなかった | `sslmode=verify-full` にし、AWS の RDS の CA 証明書を箱に入れた | 手元の箱で起動を確認（AWS では公開のときに確認） |
| 7 | データベースのバックアップ | 1日分だけ | 7日分にした（データベースの大きさまでは追加の料金なし） | `terraform plan` |
| 8 | ロードバランサー（Express Mode が作る） | HTTP の食い違い（リクエストスマグリング）への対策が Off で作られる（AWS の説明） | ALB の API で defensive にし、決まりに合わないヘッダーを捨てるスクリプトを用意した（`infra/scripts/harden-alb.sh`。公開のあとと、CD のたびに実行） | 公開のあとに、設定を表示して確認 |
| 9 | データベースのセキュリティグループ | 箱だけに絞るつもりで、AWS の説明に書かれていない組み合わせ（自分のセキュリティグループを箱に渡す）を使っていた | 箱とロードバランサーのセキュリティグループは Express Mode に作らせ（説明に書かれた動き）、データベースは VPC の中からだけ許す | `terraform plan` |

## 3. 守り方の一覧（層ごと）

### 画面（React）

- 画面に出す文字は、React がすべて文字として出す（`innerHTML`、`dangerouslySetInnerHTML`、`eval` は使っていない）。XSS の確認：名前・説明・場所・ニックネームに攻撃の文字を入れても、プログラムは動かず、文字のまま出る（Edge で確認）
- ログインの状態は、JavaScript から読めない Cookie（HttpOnly）で持つ。ブラウザの保存場所（localStorage）には、メニューの開け閉めの好みだけを入れる
- データを変える通信には、CSRF トークンを付ける
- ログインのあとの戻り先は、URL からではなく画面の中の状態から決める（外のサイトへ飛ばされない）
- 本番用のファイルに、開発用のデータや偽の API は入らない
- CSP で、このサイト以外からプログラムを読み込めないようにする

### API（Spring Boot）

| 守り | 中身 | 自動テスト |
|---|---|---|
| ログイン | パスワードは BCrypt で保存。5回失敗で15分ロック。登録されていないメールアドレスでも同じ時間をかける | AuthApiTest |
| セッション | ログインのときに番号を作り直す（セッション固定攻撃の対策）。Cookie は HttpOnly・Secure・SameSite=Lax。30分で切れる。本番はデータベースに保存 | AuthApiTest、ProdProfileSessionTest |
| CSRF | データを変える通信は、Cookie とヘッダーの合言葉が一致しないと 403（ログアウトも含む） | SecurityConfigTest、SecurityReviewTest |
| 権限 | オーナーだけ・オーナーと運営スタッフだけの操作を、すべての API で確かめる。URL の番号を書き換えても、ほかのコミュニティを操作できない | 各 API のテスト、SecurityReviewTest |
| 入力 | 受け取る項目は record に書いたものだけ（ほかの項目は無視）。文字数のチェック。1回に送れるのは 64KB まで | SecurityReviewTest、ProductionSettingsTest |
| SQL インジェクション | SQL の値は、すべて `#{}` で渡す（`${}` は使わない）。検索は `strpos` で文字として探す | SecurityReviewTest |
| 回数制限 | ログイン（10分間に20回）、アカウント登録（1時間に10回）を IP アドレスごとに制限（本番） | RateLimitTest |
| エラー | 返事に、プログラムの内部の情報（SQL、クラスの名前、Jackson のエラーの文）を入れない | SecurityReviewTest |
| HTTP ヘッダー | CSP、HSTS（HTTPS のとき）、X-Frame-Options、X-Content-Type-Options、Referrer-Policy、Permissions-Policy | ProductionSettingsTest、ProdProfileSessionTest |
| CORS | 使わない（画面と API は同じ場所）。ほかのサイトからの通信には、読んでよい許可を返さない | SecurityReviewTest |
| 個人情報 | ほかの人には、ニックネームだけを返す。本名とメールアドレスは本人にだけ | 各 API のテスト |
| ログ | パスワードをログに出さない（Spring Boot が仮の利用者のパスワードを出す仕組みを止めた） | ― |

### 箱（Docker）

- root ではない利用者で動かす
- 土台は脆弱性のもっとも少なかった Alpine 版。作るときに OS のパッケージを最新にする
- 画面と API を作る道具（Node.js、Maven、JDK）は、最後の箱に入れない

### データベースとインフラ（AWS）

- データベースはインターネットから届かない。VPC の中からだけ。暗号化した通信だけを受け付け（`rds.force_ssl=1`）、アプリは証明書で相手を確かめる
- 保存するデータは暗号化（RDS、ECR、ログ、Secrets Manager は、AWS が管理する鍵で暗号化される）
- パスワードは Secrets Manager にだけ置く。ファイルにも、Terraform の記録にも残さない
- IAM は必要な権限だけ：ECS の実行ロールは、このパスワードだけを読める。CD（GitHub Actions）は、このリポジトリの main からだけ、OIDC で一時的な権限を受け取る（アクセスキーを GitHub に置かない）
- 費用の見張り（AWS Budgets）

## 4. 受け入れたリスク（理由と、変えるときの目安）

| 内容 | 理由 | 変える目安 |
|---|---|---|
| アカウント登録で、登録済みのメールアドレスかどうかが分かる | 画面で分かりやすくするため（要件） | 利用者が増えたら、メールで確認する登録の流れに変える |
| ロックはメールアドレスごとなので、他人をわざとロックできる（15分で解ける） | IP アドレスごとの回数制限と組み合わせて、影響を小さくしている | 被害が出たら、ロックの代わりに CAPTCHA などを検討する |
| 回数制限は箱ごとに数える | 箱は最大2つなので、効果は十分 | 箱を増やすときは、データベースや Redis で数える |
| データベースは「箱だけ」ではなく「VPC の中から」許す | 箱のセキュリティグループを Express Mode が作るため、Terraform で名指しできない。VPC には、このアプリしか置かない | 同じ VPC にほかのシステムを置くときは、VPC を分ける |
| データベースのパスワードを自動で変えない | 変えると、動いている箱がつなげなくなるため（2章の5） | アプリが Secrets Manager から直接読む仕組み（AWS Advanced JDBC Wrapper）に変えたら、自動で変える |
| アプリは、データベースの管理者の利用者でつなぐ | テーブルの作成（Flyway）と、ふつうの読み書きを、同じ利用者で行っている | テーブルの作成と、読み書きの利用者を分ける |
| 削除の保護をしない（RDS の deletion protection） | ポートフォリオなので、`terraform destroy` で消しやすくするため | 大事なデータを扱うようになったら、保護して、消す前にバックアップを取る |
| AWS が管理する鍵で暗号化（自分の鍵＝CMK は使わない） | CMK は1つ月1ドルかかり、鍵の管理も必要になる。暗号化そのものはされている | 監査で、鍵の操作の記録が必要になったら |
| ECS の Container Insights、RDS の Performance Insights を使わない | 費用を抑えるため。基本の監視（CloudWatch のメトリクスとログ）はある | 動きが遅いなどの調査が必要になったら |
| RDS の IAM 認証を使わない | パスワードを Secrets Manager で守っている | 自動でパスワードを変える仕組みと合わせて検討する |

Trivy の設定チェックは、Terraform の新しい書き方（ephemeral）をまだ正しく読めないため、`rds.tf` のバックアップの日数を1日と誤って報告します（ephemeral を外したコピーで調べると、指摘は出ない）。

## 5. 版を上げるときの注意

- `backend/pom.xml` で、Tomcat と Jackson の版を上書きしている。Spring Boot の新しい版が、これより新しい版を使うようになったら、上書きを消す
- 定期的に Trivy と `npm audit` を動かす（GitHub Actions の CI に入れる。Issue #49）
