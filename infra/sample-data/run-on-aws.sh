#!/usr/bin/env bash
# 【役割】本番のデータベースに、見本のデータ（sample-data.sql）を入れるスクリプト。
# 【なぜ必要か】本番のデータベースは、インターネットから届かない場所（VPC の中）にある。データベースを外に出さずに SQL を流すため、
# AWS の中で、1回だけ動く小さな箱（ECS のタスク）を起動し、その箱から SQL を流す。
#
# 流れ：
# ① 箱の設計図（タスク定義）を登録する：PostgreSQL の道具（psql）の入った公式のイメージ。SQL は、圧縮して環境変数で渡す。
#    データベースの利用者とパスワードは、Secrets Manager から ECS が渡す（このスクリプトにも、記録にも残らない）
# ② 本番と同じネットワーク（既定の VPC）で、1回だけ動かす。終わるまで待つ
# ③ 結果（入れたデータの数）を、ログから表示する
# ④ 箱の設計図を消す（同じ名前で、次も使えるように）
#
# 注意：sample-data.sql は、はじめに【本番のデータをすべて消す】。実行する前に、内容を確かめること（README.md）。
#
# 使い方：DEMO_PASSWORD='確認用のアカウントのパスワード' bash infra/sample-data/run-on-aws.sh
set -euo pipefail
# Windows の Git Bash で、/ecs/campfires のような「/ で始まる値」を、Windows のパスに書き換えないようにする（Linux では何も起きない）
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-northeast-1}"
APP=campfires
DIR="$(cd "$(dirname "$0")" && pwd)"
: "${DEMO_PASSWORD:?DEMO_PASSWORD（確認用のアカウントのパスワード）を指定してください}"

echo "== 本番の設定を読む"
CLUSTER_ARN=$(aws ecs describe-clusters --region "$REGION" --clusters "$APP" --query 'clusters[0].clusterArn' --output text)
DB_HOST=$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$APP" --query 'DBInstances[0].Endpoint.Address' --output text)
SECRET_ARN=$(aws secretsmanager describe-secret --region "$REGION" --secret-id "$APP/db" --query ARN --output text)
EXEC_ROLE_ARN=$(aws iam get-role --role-name "$APP-ecs-execution" --query Role.Arn --output text)
VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)
SUBNETS=$(aws ec2 describe-subnets --region "$REGION" --filters Name=vpc-id,Values="$VPC_ID" Name=default-for-az,Values=true --query 'Subnets[].SubnetId' --output text | tr '\t' ',')
# 既定の VPC の default のセキュリティグループ（外への通信を許す。データベースは、VPC の中からの接続を許している）
SG=$(aws ec2 describe-security-groups --region "$REGION" --filters Name=vpc-id,Values="$VPC_ID" Name=group-name,Values=default --query 'SecurityGroups[0].GroupId' --output text)

# SQL を圧縮して、base64（文字だけの形）にする（環境変数で渡すため）
SQL_GZ_B64=$(gzip -9 -c "$DIR/sample-data.sql" | base64 -w0)

echo "== ① 箱の設計図を登録する"
# 箱の中で行うこと：RDS の CA 証明書を取ってくる → SQL を戻す → psql で流す（証明書で相手を確かめる）
# shellcheck disable=SC2016 # $SQL_GZ_B64 などは、箱の中で展開する（ここでは展開しない）ので、わざと一重引用符で囲む
SCRIPT='wget -q -O /tmp/rds-ca.pem https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem && echo "$SQL_GZ_B64" | base64 -d | gunzip > /tmp/sample-data.sql && PGPASSWORD="$DB_PASSWORD" psql "host=$DB_HOST port=5432 dbname=campfires user=$DB_USERNAME sslmode=verify-full sslrootcert=/tmp/rds-ca.pem" -q -v demo_password="$DEMO_PASSWORD" -f /tmp/sample-data.sql'
CONTAINER=$(jq -n --arg sql "$SQL_GZ_B64" --arg host "$DB_HOST" --arg demo "$DEMO_PASSWORD" --arg secret "$SECRET_ARN" --arg script "$SCRIPT" --arg region "$REGION" '[{
  name: "sample-data",
  image: "public.ecr.aws/docker/library/postgres:17-alpine",
  essential: true,
  entryPoint: ["sh", "-c"],
  command: [$script],
  environment: [{name: "SQL_GZ_B64", value: $sql}, {name: "DB_HOST", value: $host}, {name: "DEMO_PASSWORD", value: $demo}],
  secrets: [{name: "DB_USERNAME", valueFrom: ($secret + ":username::")}, {name: "DB_PASSWORD", valueFrom: ($secret + ":password::")}],
  logConfiguration: {logDriver: "awslogs", options: {"awslogs-group": "/ecs/campfires", "awslogs-region": $region, "awslogs-stream-prefix": "sample-data"}}
}]')
TASK_DEF=$(aws ecs register-task-definition --region "$REGION" --family "$APP-sample-data" \
  --requires-compatibilities FARGATE --network-mode awsvpc --cpu 256 --memory 512 \
  --execution-role-arn "$EXEC_ROLE_ARN" --container-definitions "$CONTAINER" \
  --query 'taskDefinition.taskDefinitionArn' --output text)

echo "== ② 1回だけ動かして、終わるまで待つ"
TASK_ARN=$(aws ecs run-task --region "$REGION" --cluster "$CLUSTER_ARN" --task-definition "$TASK_DEF" --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG],assignPublicIp=ENABLED}" \
  --query 'tasks[0].taskArn' --output text)
aws ecs wait tasks-stopped --region "$REGION" --cluster "$CLUSTER_ARN" --tasks "$TASK_ARN"
EXIT_CODE=$(aws ecs describe-tasks --region "$REGION" --cluster "$CLUSTER_ARN" --tasks "$TASK_ARN" --query 'tasks[0].containers[0].exitCode' --output text)

echo "== ③ 結果（ログ）"
TASK_ID="${TASK_ARN##*/}"
aws logs get-log-events --region "$REGION" --log-group-name /ecs/campfires --log-stream-name "sample-data/sample-data/$TASK_ID" \
  --query 'events[].message' --output text | tr '\t' '\n'

echo "== ④ 箱の設計図を消す"
aws ecs deregister-task-definition --region "$REGION" --task-definition "$TASK_DEF" > /dev/null

if [ "$EXIT_CODE" != "0" ]; then
  echo "失敗しました（終了コード $EXIT_CODE）。SQL は1つのまとまり（トランザクション）で流すので、途中で失敗したときは、何も変わっていません" >&2
  exit 1
fi
echo "見本のデータを入れました"
