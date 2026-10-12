#!/usr/bin/env bash
# 【役割】Terraform の記録（terraform.tfstate）を置く S3 のバケットを作るスクリプト（最初に1回だけ使う）。
# 【なぜ必要か】記録が手元のパソコンにだけあると、消えたときに、Terraform で AWS を管理できなくなる。
# S3 に置けば、版ごとに残り（間違って壊しても前の版に戻せる）、別のパソコンからも同じ記録を使える。
# このバケット自身は、Terraform の記録を置く場所なので、Terraform ではなく、このスクリプトで作る（鶏と卵の問題を避ける）。
#
# 作ったあと、表示された内容を infra/backend.hcl（Git に入れない）に書き、terraform init -migrate-state -backend-config=backend.hcl を行う
# （infra/README.md の「Terraform の記録（S3）」）。
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-1}"
# バケットの名前は、世界中で1つだけにする必要があるので、AWS のアカウントの番号を付ける
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
BUCKET="campfires-tfstate-${ACCOUNT}"

echo "== バケットを作る：$BUCKET"
aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
  --create-bucket-configuration LocationConstraint="$REGION" > /dev/null

echo "== 外からの公開を、すべて禁止する"
aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

echo "== 版を残す（記録を上書きしても、前の版に戻せる）"
aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled

echo "== 保存するときに暗号化する（S3 が管理する鍵）"
aws s3api put-bucket-encryption --bucket "$BUCKET" --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'

echo "== 古い版は90日で消す（置いておく分だけ料金がかかるため）"
aws s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" --lifecycle-configuration \
  '{"Rules":[{"ID":"expire-old-versions","Status":"Enabled","Filter":{},"NoncurrentVersionExpiration":{"NoncurrentDays":90}}]}'

echo "== 暗号化していない通信（http）を断る"
aws s3api put-bucket-policy --bucket "$BUCKET" --policy "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [{
    \"Sid\": \"DenyInsecureTransport\",
    \"Effect\": \"Deny\",
    \"Principal\": \"*\",
    \"Action\": \"s3:*\",
    \"Resource\": [\"arn:aws:s3:::${BUCKET}\", \"arn:aws:s3:::${BUCKET}/*\"],
    \"Condition\": {\"Bool\": {\"aws:SecureTransport\": \"false\"}}
  }]
}"

echo
echo "できました。infra/backend.hcl に、次の1行を書いてください："
echo "bucket = \"$BUCKET\""
