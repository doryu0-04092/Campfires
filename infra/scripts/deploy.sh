#!/usr/bin/env bash
# 【役割】ECS Express Mode のアプリを、新しい Docker イメージに入れ替えるスクリプト（CD。GitHub Actions から呼ぶ。手元でも使える）。
# 【なぜ必要か】イメージだけを入れ替え、ほかの設定（環境変数、パスワードの読み先、ログ、台数など）は Terraform で決めたまま残すため。
#
# やること：
# ① 今の設定を読み（DescribeExpressGatewayService）、primaryContainer の image だけを書き換えて、設定を送り直す（UpdateExpressGatewayService）。
#    AWS の説明（UpdateExpressGatewayService）には、primaryContainer の一部だけを送ったときに、残りの項目が保たれるかが書かれていない。
#    そのため、今の primaryContainer をまるごと送り、どちらの動きでも、イメージ以外が変わらないようにする。
# ② この入れ替えで作られた「デプロイ」（ServiceDeployment）が終わるまで待つ。
#    Express Mode は、カナリア（CANARY）という方法で入れ替える：まず 5% の通信を新しい箱に流して 3分見守り、
#    残りを切り替えて、さらに 3分見守る（箱の起動を含めて、15分ほどかかる）。
#    途中で新しい箱が正しく動かなければ、ECS が前の設定に自動で戻す（ロールバック）。
#    ECS の決まった待ち方（services-stable）は 10分で打ち切られ、カナリアの終わりも分からないため、デプロイの状態を直接見る。
#    SUCCESSFUL なら成功。ROLLBACK_〜、STOPPED、FAILED なら失敗（戻ったので、前のイメージのまま動いている）。
#
# 使い方：infra/scripts/deploy.sh <サービスの ARN> <新しいイメージ（リポジトリの URL:タグ）>
set -euo pipefail

SERVICE_ARN="$1"
IMAGE="$2"
# サービスの ARN（arn:aws:ecs:地域:番号:service/クラスター/名前）から、クラスターの名前を取り出す
CLUSTER=$(echo "$SERVICE_ARN" | cut -d/ -f2)
# 待つ時間の上限（秒）。カナリアの見守り（3分×2）と、箱の起動に、余裕を持たせて30分
TIMEOUT_SECONDS=1800

echo "== ① イメージを入れ替える：$IMAGE"
# jq：JSON を読み書きする道具。activeConfigurations[0] が、今動いている設定
CONTAINER=$(aws ecs describe-express-gateway-service --service-arn "$SERVICE_ARN" --output json   | jq --arg image "$IMAGE" '.service.activeConfigurations[0].primaryContainer | .image = $image')
# 返事に入っている「新しい設定の番号（サービスリビジョン）」を覚えておき、②で、この入れ替えのデプロイを見つけるのに使う
REVISION=$(aws ecs update-express-gateway-service --service-arn "$SERVICE_ARN"   --primary-container "$CONTAINER" --query 'service.targetConfiguration.serviceRevisionArn' --output text)
echo "新しい設定：$REVISION"

echo "== ② 入れ替え（デプロイ）が終わるまで待つ（15分ほどかかる）"
WAITED=0
while true; do
  # 新しい順に5つのデプロイから、この入れ替えのもの（新しい設定に切り替えるもの）を探す
  DEPLOYMENT=$(aws ecs list-service-deployments --cluster "$CLUSTER" --service "$SERVICE_ARN" --max-results 5     --query "serviceDeployments[?targetServiceRevisionArn=='$REVISION'] | [0].serviceDeploymentArn" --output text)
  STATUS="NOT_FOUND"
  if [ "$DEPLOYMENT" != "None" ]; then
    STATUS=$(aws ecs describe-service-deployments --service-deployment-arns "$DEPLOYMENT"       --query 'serviceDeployments[0].[status, lifecycleStage]' --output text)
  fi
  echo "$(date -u +%H:%M:%S) $STATUS"
  # case は「値によって分ける」書き方。* は「何でも」
  case "$STATUS" in
    SUCCESSFUL*)
      echo "入れ替わりました：$IMAGE"
      exit 0 ;;
    ROLLBACK_*|STOPPED*|FAILED*)
      echo "新しいイメージが正しく動かず、ECS が前の設定に戻しました（$STATUS）。ECS のサービスのイベントと、アプリのログを確かめてください" >&2
      exit 1 ;;
  esac
  if [ "$WAITED" -ge "$TIMEOUT_SECONDS" ]; then
    echo "$TIMEOUT_SECONDS 秒待っても終わりませんでした。AWS の画面で、デプロイの状態を確かめてください" >&2
    exit 1
  fi
  sleep 20
  WAITED=$((WAITED + 20))
done
