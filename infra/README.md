# infra（AWS の構成）

<!--
  【役割】Campfires を AWS に公開するための Terraform の使い方と、運用の手順をまとめた文書。
  【なぜ必要か】作る・消す・パスワードを変えるときの手順を残し、だれがやっても同じ手順で、安全に行えるようにするため。
-->

## 構成

ECS Express Mode（Fargate とロードバランサーと HTTPS の証明書を、まとめて作る仕組み）で 1 つの箱（画面と API）を動かし、データベースは RDS for PostgreSQL 17 を使います。地域は東京（ap-northeast-1）です。

| ファイル | 中身 |
|---|---|
| versions.tf | Terraform と、プロバイダーの版 |
| variables.tf | 変えられる値（terraform.tfvars で値を入れる） |
| network.tf | 既定の VPC と、データベースのセキュリティグループ |
| ecr.tf | Docker イメージの置き場所 |
| rds.tf | データベースと、そのパスワード（Secrets Manager） |
| iam.tf | ECS と、箱の中のアプリが使う権限 |
| ecs.tf | アプリの箱（ECS Express Mode） |
| github_oidc.tf | CD（GitHub Actions）が使う権限 |
| budget.tf | 費用の見張り（メールで知らせる） |
| tracing.tf | トレースの受け取り（Transaction Search） |
| monitoring.tf | ログから作る数字、アラーム、ダッシュボード、保存したログの検索 |
| alb.tf | ロードバランサーに、HTTP（80番）で来たら HTTPS へ移動させる入口を足す |
| scripts/harden-alb.sh | Express Mode が作ったロードバランサーの守りを強くする |
| scripts/deploy.sh | アプリを新しいイメージに入れ替える（CD。手で前の版に戻すときにも使う） |
| scripts/create-state-bucket.sh | Terraform の記録を置く S3 のバケットを作る（最初に1回だけ） |

## 準備

1. `terraform.tfvars.example` を `terraform.tfvars` にコピーし、値を入れる（`terraform.tfvars` はリポジトリに入れない）
2. AWS の認証（`aws configure` など）。アクセスキーは、リポジトリに入れない
3. `backend.hcl.example` を `backend.hcl` にコピーし、Terraform の記録を置く S3 のバケットの名前を入れる（下の「Terraform の記録（S3）」）
4. `terraform init -backend-config=backend.hcl`

## Terraform の記録（S3）

Terraform の記録（どの AWS の部品を作ったか。`terraform.tfstate`）は、S3 のバケットに置いています（`versions.tf` の `backend "s3"`）。

- 版を残す（間違って壊しても、前の版に戻せる。古い版は90日で消える）、暗号化、外からの公開の禁止、暗号化していない通信の禁止
- `use_lockfile`：2人が同時に `terraform apply` しても、記録が壊れないように、S3 に「使用中」の印を置く
- バケットは、最初に1回だけ `scripts/create-state-bucket.sh` で作る（バケット自身は、Terraform の記録を置く場所なので、Terraform では作らない）
- バケットの名前にはアカウントの番号が入るので、`backend.hcl` は Git に入れない

手元にあった記録から移したときは `terraform init -migrate-state -backend-config=backend.hcl` を使った（2026年10月12日）。

## 作る

1. `terraform apply -target=aws_ecr_repository.app`（先にイメージの置き場所だけを作る）
2. Docker イメージを作り、ECR に上げる（タグは `terraform.tfvars` の `image_tag` と同じにする）
3. `terraform apply`
4. `scripts/harden-alb.sh`（ロードバランサーの守りを強くする）
5. AWS から届く「購読の確認」のメール（アラームの知らせ）のリンクを押す
6. もう一度 `terraform apply` を行う（Transaction Search が作ったトレースの置き場所 `aws/spans` を、Terraform の管理に取り込み、14日で消す設定にする。`tracing.tf` の ③。AWS が作ったときは 30日）
7. `terraform output app_url` の URL を開いて確かめる。ダッシュボードは `terraform output dashboard_url`

## 見張り（ログ・メトリクス・トレース）

[docs/observability.md](../docs/observability.md) を参照。

Transaction Search（`tracing.tf`）は、この AWS アカウントの東京リージョン全体の設定です。`terraform destroy` のあとも有効のまま残ります。元に戻すときは、次を実行します。

```
aws xray update-trace-segment-destination --destination XRay
```

## データベースのパスワードを変える

パスワードは、自動では変わりません（理由は [docs/security.md](../docs/security.md) の 2章の5）。漏れたおそれがあるときなどは、次の手順で変えます。

1. `rds.tf` の `password_version` を 1 増やす
2. `terraform apply`（新しいパスワードを作り、データベースと Secrets Manager に書き込む）
3. ECS の箱を入れ替える（箱は起動のときにパスワードを受け取るので、入れ替えるまで新しいパスワードを使わない）。
   AWS の説明（Updating Resources Outside of Express Mode）にある「Force new deployment」と同じ操作を、CLI で行う。
   サービスの名前は `terraform output ecs_service_arn` の最後の部分

   ```
   aws ecs update-service --cluster campfires --service <サービスの名前> --force-new-deployment
   ```

2 と 3 の間は、動いている箱が新しくデータベースにつなげなくなります（すでにつないでいる分は使える）。手順は続けて行ってください。

### `terraform apply` が途中で止まったとき

パスワードは、Terraform を実行するたびに作り直されます（記録に残さないため）。データベースと Secrets Manager が、別々の回の実行で作られると、パスワードがずれます。アプリのログに `password authentication failed` が出て、起動できません。

そのときは、上の手順の 1 と 2（`password_version` を 1 増やして `terraform apply`）を行います。1回の実行の中で、両方に同じ新しいパスワードが書き込まれます（最初の公開で、無料プランの制限でデータベースだけが別の回に作られ、実際に起きた）。

## 消す

`terraform destroy`（データベースの中身も消えます。残したいときは、先に RDS のスナップショットを取る）
