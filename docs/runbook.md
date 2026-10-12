# 障害のときの手順書（ランブック）

<!--
  【役割】本番で困ったことが起きたときに、何を見て、どう直すかの手順。
  【なぜ必要か】非機能要件の「故障からの復旧：4時間以内（手順書を作り、実際に試す）」「データのバックアップ：戻す手順を実際に試す」を満たすため。
  慌てているときでも、上から順に進めれば直せるようにする。
-->

## 0. まず見るところ

| 見るもの | 場所 |
|---|---|
| 動いているか（外から） | GitHub の Actions の「Uptime」（15分ごと）。手で確かめるなら `curl https://（本番の URL）/api/health` |
| 数字（エラー、応答時間、CPU、データベース） | CloudWatch のダッシュボード `campfires`（`terraform output dashboard_url`） |
| ログ | CloudWatch Logs Insights の保存したクエリ `campfires/errors`、`campfires/slow-requests`（docs/observability.md） |
| アプリの状態 | ECS → クラスター `campfires` → サービス `campfires` の「イベント」「デプロイ」 |

## 1. アプリが止まった・応答しない

ECS は、箱が止まったり、死活の確認（`/api/health`）に答えなくなったりすると、自動で新しい箱に入れ替えます。まず数分待ちます。

直らないとき：

1. ECS のサービスの「イベント」で、止まった理由を見る（`Essential container in task exited` など）
2. CloudWatch Logs の `/ecs/campfires` で、起動のときのエラーを見る（`Application run failed` の行と、その下の `Caused by`）
3. 原因ごとに：

| ログの内容 | 原因 | 直し方 |
|---|---|---|
| `password authentication failed` | データベースのパスワードのずれ | infra/README.md の「パスワードを変える」（`password_version` を1増やして `terraform apply`） |
| `Unable to obtain connection` が続く | データベースが止まっている | 3章 |
| `OutOfMemoryError` | メモリ不足 | `infra/ecs.tf` の `memory` を上げて `terraform apply` |
| 新しい版の公開のあとから起きた | 新しい版の不具合 | 2章 |

**手元で確かめたこと（2026年10月）：**
- 間違ったパスワードで起動すると、25秒ほどで箱が止まる（終了コード 1）。これにより ECS が異常に気づき、公開のときは前の版に戻す
- データベースを止めると、死活の確認は 503、API は 500（決まった形、内部の情報なし）を、5秒で返す。箱は止まらない。データベースを戻すと、2秒ほどで自動で元に戻る

## 2. 新しい版の公開（CD）のあとから、おかしくなった

公開の途中で新しい箱が正しく動かなければ、ECS が自動で前の版に戻し、CD は失敗になります（README の CI/CD）。公開が成功したあとに不具合に気づいたときは、前の版のイメージに戻します。

```bash
# 1. ECR にある前の版のイメージ（名前はコミットの番号）を確かめる
aws ecr describe-images --repository-name campfires --query "sort_by(imageDetails,&imagePushedAt)[-5:].[imageTags[0],imagePushedAt]" --output table

# 2. 前の版に入れ替える（15分ほど）
bash infra/scripts/deploy.sh "$(cd infra && terraform output -raw ecs_service_arn)" "（ECR の URL）:（前の版のコミットの番号）"
```

そのあと、不具合を直した PR を main にマージすれば、CD で新しい版が公開されます。

## 3. データベースが壊れた・データを間違って消した

RDS は、毎日の自動バックアップと、直前（5分ほど前）までの変更の記録を持っています。**決めた時刻の状態に戻した、新しいデータベース**を作り、アプリのつなぎ先を切り替えます（元のデータベースは、そのまま残る）。

1. 戻したい時刻を決める（例：データを消してしまう直前）。戻せる範囲を確かめる

   ```bash
   aws rds describe-db-instances --db-instance-identifier campfires --query "DBInstances[0].[EarliestRestorableTime,LatestRestorableTime]"
   ```

2. その時刻の状態で、新しいデータベースを作る（10分ほど。練習では9分）。直前の状態にするなら、`--restore-time` の代わりに `--use-latest-restorable-time`

   ```bash
   aws rds restore-db-instance-to-point-in-time --source-db-instance-identifier campfires \
     --target-db-instance-identifier campfires-restored --restore-time 2026-10-12T09:00:00Z \
     --db-instance-class db.t4g.micro --db-subnet-group-name campfires --vpc-security-group-ids （infra の aws_security_group.db の ID） \
     --db-parameter-group-name campfires --no-publicly-accessible
   ```

3. 中身を確かめてから、アプリのつなぎ先（`infra/ecs.tf` の `DB_HOST`）を、新しいデータベースに変える（または、元の名前に付け替える）
4. 切り替えが終わり、問題がなければ、古いデータベースを消す

パスワードは、元のデータベースと同じものが引き継がれます。

**練習した結果：**
- 手元（2026年10月12日）：1万件のデータで、バックアップを取って新しいデータベースに戻し、すべての表の件数が同じになることを確かめた（8秒）
- AWS（2026年10月12日）：本番のデータベースから、直前の状態の新しいデータベースを作り（9分）、中身（確認用のアカウント、コミュニティ、イベント、テーブルの版）が本番と同じことを確かめてから消した。
  確かめるため、練習のときだけ、新しいデータベースに公開の住所を付け、自分の IP アドレスだけを通すセキュリティグループを付けた（本番の切り替えのときは不要）

## 4. 料金が予算を超えそう・アカウントの期限

- 予算の知らせ（AWS Budgets）が来たら、Billing の画面で、どのサービスが高いかを見る
- 無料プランは、クレジットがなくなるか、期限（2027年1月7日）が来ると、アカウントが自動で閉じられる。その前に、有料プランに上げるか、`terraform destroy` で消す

## 5. 全部消して、作り直す

```bash
cd infra
terraform destroy   # データベースの中身も消える。残したいときは、先に RDS のスナップショットを取る
terraform apply     # 作り直す（README の「作る」の手順）
```

Transaction Search の設定は、destroy のあとも残ります（infra/README.md）。
