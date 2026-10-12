# Campfires

[![CI/CD](https://github.com/doryu0-04092/Campfires/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/doryu0-04092/Campfires/actions/workflows/ci-cd.yml)

勉強会・コミュニティ運営のためのイベント申込み管理アプリ

## フォルダ

| フォルダ | 中身 | 説明 |
|---|---|---|
| `frontend` | 画面（React、TypeScript、Vite） | [frontend/README.md](frontend/README.md) |
| `backend` | API（Java、Spring Boot、MyBatis、PostgreSQL） | [backend/README.md](backend/README.md) |
| `docs` | 要件定義書、画面一覧、API 仕様書、セキュリティ、オブザーバビリティなど | |
| `e2e` | E2E テスト（Playwright。API だけのテストと、ブラウザのテスト） | [e2e/README.md](e2e/README.md) |
| `infra` | AWS の構成（Terraform） | [infra/README.md](infra/README.md) |

## 動かし方

画面だけを動かす方法と、画面と API（バックエンド）を一緒に動かす方法があります。

### 画面だけ（偽の API を使う）

```
cd frontend
npm install
npm run dev
```

http://localhost:5173 を開きます。偽の API（MSW）がブラウザの中で返事をするので、Java や Docker がなくても動きます。

### 画面と API を一緒に（本物の API を使う）

Java 21 と Docker Desktop が必要です。ターミナルを2つ開きます。

```
# 1つ目：API（http://localhost:8080）。PostgreSQL も Docker で自動で起動する
cd backend
./mvnw spring-boot:run

# 2つ目：画面（http://localhost:5173）。/api への通信を、1つ目の API に転送する
cd frontend
npm run dev:api
```

http://localhost:5173 を開きます。どちらの方法でも、同じ開発用のアカウント（[frontend/README.md](frontend/README.md) の「開発用のアカウント」）でログインできます。

### 本番用の箱（Docker イメージ）を手元で動かす

本番では、画面と API を1つの Docker イメージにまとめ、Spring Boot が画面のファイルも返します（`Dockerfile`）。
本番の設定（`backend/src/main/resources/application-prod.properties`）で動くので、データベースのつなぎ先とパスワードは環境変数で渡します。

```
# イメージを作る（リポジトリのいちばん上のフォルダで）
docker build -t campfires .

# 本番用のデータベースと、アプリの箱を、同じネットワークで動かす
# （パスワードは、手元で試すための例。本番では AWS の Secrets Manager から渡す）
docker network create campfires-prod
docker run -d --name campfires-db --network campfires-prod \
  -e POSTGRES_DB=campfires -e POSTGRES_USER=campfires -e POSTGRES_PASSWORD=change-me postgres:17
docker run -d --name campfires-app --network campfires-prod -p 8081:8080 \
  -e DB_HOST=campfires-db -e DB_USERNAME=campfires -e DB_PASSWORD=change-me -e DB_SSL_MODE=disable campfires
```

http://localhost:8081 を開きます。本番の設定では、開発用のデータは入らないので、アカウント登録から始めます。

| 環境変数 | 内容 | 例 |
|---|---|---|
| `DB_HOST` | データベースの場所（必須） | `campfires-db` |
| `DB_PORT` | データベースの入口の番号 | `5432`（省略したとき） |
| `DB_NAME` | データベースの名前 | `campfires`（省略したとき） |
| `DB_USERNAME`、`DB_PASSWORD` | データベースの利用者とパスワード（必須） | |
| `DB_SSL_MODE` | データベースとの通信の暗号化と、証明書の確かめ方。手元の PostgreSQL（暗号化なし）につなぐときは `disable` | `verify-full`（省略したとき。AWS の RDS 用） |
| `CAMPFIRES_RATE_LIMIT_ENABLED` | ログイン・アカウント登録の回数制限を使うか | `true`（省略したとき） |
| `OTEL_TRACES_EXPORTER` など | トレースの送り先（[docs/observability.md](docs/observability.md)） | `none`（省略したとき。送らない） |

本番の設定で、開発中と違うところ

- ログインの状態（セッション）を、データベースに保存する（箱を入れ替えたり増やしたりしても、ログインが切れない）
- ログイン・アカウント登録の回数を、IP アドレスごとに制限する（ログインは10分間に20回、アカウント登録は1時間に10回まで）
- ロードバランサーが付けるヘッダーを読み、HTTPS で来たことと、利用者の本当の IP アドレスを正しく扱う
- 死活監視の URL：`/api/health`（データベースにつながらなければ 503）
- ログを JSON で出し、API の呼び出しごとに1行のアクセスログを残す（[docs/observability.md](docs/observability.md)）

この箱に対して、E2E テストを動かせます（[e2e/README.md](e2e/README.md)）。

## CI/CD（GitHub Actions）

`.github/workflows/ci-cd.yml` で、次の流れを自動で行います。

| いつ | 何をするか |
|---|---|
| PR を出したとき・main にマージしたとき（CI） | バックエンドのテスト（`./mvnw test`）、画面の lint・ビルド・`npm audit`、秘密の値の検査（secretlint）、Terraform の書き方の確認、本番用の Docker イメージを作って脆弱性の検査（Trivy）、そのイメージを動かして E2E（API とブラウザ） |
| main にマージされ、CI がすべて通ったとき（CD） | CI で確かめたイメージを ECR に上げ（名前はコミットの番号）、AWS のアプリを入れ替える。入れ替えが終わるまで待ち、ロードバランサーの守りの設定と、公開したアプリを API の E2E で確かめる |

- AWS へは OIDC でログインします（AWS のアクセスキーを GitHub に置かない）。GitHub に登録する値は、Secret の `AWS_DEPLOY_ROLE_ARN`（`terraform output github_deploy_role_arn`）だけです
- 新しいイメージが動かなかったときは、ECS が前のイメージに自動で戻し、CD は失敗になります
- データベースのテーブルの変更（Flyway）は、アプリが起動したときに自動で行われます
- 公開のたびに、本番で API の E2E を行います（テスト用の利用者を3人登録して、最後に消す）。本番の回数制限（登録は1時間に10回）があるので、**1時間に3回より多くマージしない**でください
