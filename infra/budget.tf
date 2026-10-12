# 【役割】1か月の AWS の請求額を見張り、予算を超えそうになったらメールで知らせる設定（AWS Budgets）。
# 【なぜ必要か】クラウドは、使った分だけ料金がかかる。設定の間違いや、消し忘れで、思わぬ金額にならないようにするため。
# 知らせるだけで、自動で止めることはしない。知らせが来たら、内容を確かめ、必要なら terraform destroy で消す。

resource "aws_budgets_budget" "monthly" {
  name         = "${var.app_name}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_limit_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # 実際の金額が、予算の 80% を超えたとき
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  # 今のペースだと、月末に予算を超えそうなとき（見込み）
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}
