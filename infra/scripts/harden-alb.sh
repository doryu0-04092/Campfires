#!/usr/bin/env bash
# 【役割】ECS Express Mode が作ったロードバランサー（ALB）の、セキュリティの設定を固めるスクリプト。
# 【なぜ必要か】Express Mode は、ALB を次の既定で作る（AWS の説明「Resources created by Amazon ECS Express Mode services」）。
#   - desync-mitigation-mode: Off（HTTP のリクエストの食い違い＝リクエストスマグリングへの対策が無効）
# AWS の説明には「これらは Application Load Balancer で設定できる」とあるので、ALB の API で次のように変える。
#   - routing.http.desync_mitigation_mode = defensive（あやしいリクエストを、食い違いが起きないように扱う）
#   - routing.http.drop_invalid_header_fields.enabled = true（決まりに合わない名前のヘッダーを捨てる）
# 何度実行しても同じ結果になる。terraform apply のあとと、CD（GitHub Actions）で新しいイメージに入れ替えたあとに実行する。
#
# 使い方：bash infra/scripts/harden-alb.sh
set -euo pipefail

# Express Mode が作った ALB を探す（Express Mode が作ったものには、AmazonECSManaged=true の目印が付く）
LB_ARNS=$(aws resourcegroupstaggingapi get-resources \
  --resource-type-filters elasticloadbalancing:loadbalancer \
  --tag-filters Key=AmazonECSManaged,Values=true \
  --query 'ResourceTagMappingList[].ResourceARN' --output text)

if [ -z "$LB_ARNS" ]; then
  echo "Express Mode の ALB が見つかりません（まだ作られていない可能性があります）" >&2
  exit 1
fi

for LB_ARN in $LB_ARNS; do
  echo "== $LB_ARN"
  aws elbv2 modify-load-balancer-attributes --load-balancer-arn "$LB_ARN" --attributes \
    Key=routing.http.desync_mitigation_mode,Value=defensive \
    Key=routing.http.drop_invalid_header_fields.enabled,Value=true \
    --query 'Attributes[?Key==`routing.http.desync_mitigation_mode` || Key==`routing.http.drop_invalid_header_fields.enabled`]' \
    --output table
  # HTTPS の入口（443）の、暗号化の決まり（TLS のセキュリティポリシー）を表示する（確かめるため）
  aws elbv2 describe-listeners --load-balancer-arn "$LB_ARN" \
    --query 'Listeners[].{port:Port,protocol:Protocol,sslPolicy:SslPolicy}' --output table
done
