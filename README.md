# Campfires

[![CI/CD](https://github.com/doryu0-04092/Campfires/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/doryu0-04092/Campfires/actions/workflows/ci-cd.yml)

## 公開しているアプリ

**URL：** https://ca-9b0f538e8d494616982abeb2dcb41aa5.ecs.ap-northeast-1.on.aws

**確認用のアカウント**

| ログイン ID（メールアドレス） | パスワード |
|---|---|
| `demo@example.com` | `campfires-demo-2026` |

このアカウントで、一通りの機能を確かめられます。

- **オーナーとして：** 自分のコミュニティ 3件（とアーカイブ済み 1件）の管理。メンバー管理、参加の申請の承認、イベントの作成・編集、申込者一覧での承認・却下（キャンセル待ちや、締め切り後のイベントもある）
- **利用者として：** ほかのコミュニティ 5件のメンバー。イベント 5件に申込み（申込み済み・承認待ち・キャンセル待ち・却下）、お知らせ

入っているのは、見本のデータです（利用者 30人、コミュニティ 12件、イベント 24件。[infra/sample-data/](infra/sample-data/README.md)）。だれでもログインできるので、データは時々入れ直すことがあります。

## Campfires とは

勉強会・コミュニティ運営のためのイベント申込み管理アプリです。
コミュニティを作り、メンバーを集め、イベントの参加者を募集し、申込みを管理できます。

## デモ動画

ログインしていない「さくら」が、オーナー「ゆうき」のイベントを見つけてログインし、申し込みます。ゆうきが、そのさくらの申込みを承認し、新しいイベントを作るまでの流れです（約2分。動画は、録画用の見本のデータで撮っています）。

<!-- ↓ 次の1行を、動画の URL（https://github.com/user-attachments/assets/…）に置き換える。URL だけを1行で書き、前後の空いた行は残す -->

https://github.com/user-attachments/assets/3625fd82-6f5a-46a2-aa37-7fa5489911e7

## 主な機能

| だれが | できること |
|---|---|
| ログインしていない人 | イベント・コミュニティの検索と、詳細を見る。「申し込む」を押すとログインの画面へ移り、ログインのあと、元の画面に戻る |
| 利用者（ログインした人） | イベントに申し込む・取り消す（満席ならキャンセル待ち。空きが出たら自動で繰り上がる）、コミュニティへの参加の申請、参加予定のイベントの一覧、お知らせ、ニックネーム・パスワードの変更、退会 |
| オーナー・運営スタッフ | コミュニティの作成・編集・アーカイブ、参加の申請の承認、メンバー管理（運営スタッフの任命・除名）、イベントの作成・編集・削除、申込者の一覧と、申込みの承認・却下 |

イベントごとに、参加の承認の方法（自動・手動）を、メンバーとメンバー以外で選べます。定員は、同時にたくさんの申込みが来ても超えないようにしています。

## 使っている技術

| 分野 | 技術 |
|---|---|
| 画面 | React 19、TypeScript、Vite、React Router |
| API | Java 21、Spring Boot 4、Spring Security、MyBatis、Flyway |
| データベース | PostgreSQL 17 |
| インフラ | AWS（ECS Express Mode・Fargate、RDS、Secrets Manager、ECR）、Terraform |
| CI/CD | GitHub Actions（テストがすべて通ったときだけ、自動で公開） |
| テスト | JUnit 5・Testcontainers（393件）、Playwright の E2E（292件。Edge・Firefox・WebKit・スマートフォン・アクセシビリティ）、k6 の負荷テスト |
| 見張り | CloudWatch（ログ・数字・アラーム）、AWS X-Ray（トレース。OpenTelemetry） |

## 構成

```
[ブラウザ]  React の画面
    │  HTTPS
    ▼
[ロードバランサー]  AWS（ECS Express Mode が作る）
    │
    ▼
[アプリ]  Spring Boot（画面のファイルと API を、1つの Docker イメージで返す。ECS Fargate）
    │  暗号化した接続
    ▼
[データベース]  PostgreSQL（Amazon RDS）
```

## 文書

| 文書 | 中身 |
|---|---|
| [要件定義書](docs/requirements.md) | 機能と業務のルール |
| [画面一覧](docs/screens.md) | 画面と URL |
| [API 仕様書](docs/api/README.md) | API の決まりと一覧（OpenAPI） |
| [非機能要件](docs/non-functional-requirements.md) | 性能・セキュリティ・運用の目標と、確かめた結果 |
| [セキュリティ](docs/security.md) | 見直しの結果と、受け入れたリスク |
| [オブザーバビリティ](docs/observability.md) | ログ・メトリクス・トレース |
| [障害のときの手順書](docs/runbook.md) | 復旧の手順と、練習の結果 |
| [技術構成](docs/tech-stack.md) | 技術を選んだ理由 |


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
- 入れ替えは、Express Mode の決まった方法（カナリア）で行います。まず 5% の通信を新しいアプリに流して 3分見守り、残りを切り替えて、さらに 3分見守ります。CD は、全部で 15分ほどかかります（`infra/scripts/deploy.sh`）
- 新しいイメージが動かなかったときは、ECS が前のイメージに自動で戻し、CD は失敗になります
- データベースのテーブルの変更（Flyway）は、アプリが起動したときに自動で行われます
- 公開のたびに、本番で API の E2E を行います（テスト用の利用者を3人登録して、最後に消す）。本番の回数制限（登録は1時間に10回）があるので、**1時間に3回より多くマージしない**でください
