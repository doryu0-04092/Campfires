#!/usr/bin/env bash
# 【役割】ECS Express Mode のアプリを、新しい Docker イメージに入れ替えるスクリプト（CD。GitHub Actions から呼ぶ。手元でも使える）。
# 【なぜ必要か】イメージだけを入れ替え、ほかの設定（環境変数、パスワードの読み先、ログ、台数など）は Terraform で決めたまま残すため。
#
# やること：
# ① 今の設定を読み（DescribeExpressGatewayService）、primaryContainer の image だけを書き換えて、設定を送り直す（UpdateExpressGatewayService）。
#    AWS の説明（UpdateExpressGatewayService）には、primaryContainer の一部だけを送ったときに、残りの項目が保たれるかが書かれていない。
#    そのため、今の primaryContainer をまるごと送り、どちらの動きでも、イメージ以外が変わらないようにする。
# ② 入れ替えが終わり、落ち着くまで待つ（ECS が、新しい箱が正しく動くことを確かめる。動かなければ、前のイメージに自動で戻す）
# ③ 今動いている設定のイメージが、新しいイメージになっていることを確かめる（自動で戻っていたら、失敗にする）
#
# 使い方：infra/scripts/deploy.sh <サービスの ARN> <新しいイメージ（リポジトリの URL:タグ）>
set -euo pipefail

SERVICE_ARN="$1"
IMAGE="$2"
# サービスの ARN（arn:aws:ecs:地域:番号:service/クラスター/名前）から、クラスターの名前を取り出す
CLUSTER=$(echo "$SERVICE_ARN" | cut -d/ -f2)

echo "== ① イメージを入れ替える：$IMAGE"
# jq：JSON を読み書きする道具。activeConfigurations[0] が、今動いている設定
CONTAINER=$(aws ecs describe-express-gateway-service --service-arn "$SERVICE_ARN" --output json \
  | jq --arg image "$IMAGE" '.service.activeConfigurations[0].primaryContainer | .image = $image')
aws ecs update-express-gateway-service --service-arn "$SERVICE_ARN" \
  --primary-container "$CONTAINER" --query 'service.targetConfiguration.primaryContainer.image' --output text

echo "== ② 入れ替えが終わるまで待つ（数分かかる）"
# services-stable：箱の数が決めた数になり、古い箱が止まるまで待つ（15秒ごとに確かめ、最大40回＝10分）
aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE_ARN"

echo "== ③ 動いているイメージを確かめる"
RUNNING=$(aws ecs describe-express-gateway-service --service-arn "$SERVICE_ARN" \
  --query 'service.activeConfigurations[0].primaryContainer.image' --output text)
if [ "$RUNNING" != "$IMAGE" ]; then
  # ECS が、新しいイメージを動かせずに、前のイメージに戻した
  echo "新しいイメージに入れ替わっていません（動いているのは $RUNNING）。ECS のサービスのイベントと、アプリのログを確かめてください" >&2
  exit 1
fi
echo "入れ替わりました：$RUNNING"
