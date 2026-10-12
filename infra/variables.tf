# 【役割】このフォルダの Terraform で使う「変えられる値」（変数）の一覧。
# 【なぜ必要か】リージョンや予算などを、コードを書き換えずに変えられるようにするため。
# 値は terraform.tfvars（手元だけ。Git に上げない）に書く。見本は terraform.tfvars.example。

variable "region" {
  description = "AWS のリージョン（東京）"
  type        = string
  default     = "ap-northeast-1"
}

variable "app_name" {
  description = "部品の名前の頭に付ける、アプリの名前"
  type        = string
  default     = "campfires"
}

variable "image_tag" {
  description = <<-EOT
    最初に公開する Docker イメージのタグ（名前）。ECR に上げたイメージのタグを指定する。
    2回目からは、CD（GitHub Actions）が新しいイメージに入れ替えるので、Terraform はイメージを変えない（ecs.tf の ignore_changes）
  EOT
  type        = string
}

variable "budget_limit_usd" {
  description = "1か月の予算（ドル）。超えそうになったら、メールで知らせる"
  type        = number
  default     = 70
}

variable "alert_email" {
  description = "予算と、アプリの異常（monitoring.tf）の知らせを送るメールアドレス（terraform.tfvars に書く。リポジトリには入れない）"
  type        = string
  sensitive   = true
}

variable "github_oidc_subject_prefix" {
  description = <<-EOT
    GitHub が OIDC の身分証（sub）に入れる、リポジトリの名前の部分。「変わらない番号を使う形」なので、持ち主とリポジトリの番号が入る。
    調べ方：gh api repos/持ち主/名前/actions/oidc/customization/sub の sub_claim_prefix（番号は公開されている情報）
  EOT
  type        = string
  default     = "repo:doryu0-04092@292095077/Campfires@1389197570"
}

variable "trace_sampling_ratio" {
  description = <<-EOT
    トレースを記録するリクエストの割合（0〜1）。1 は、すべてのリクエストを記録する。
    ポートフォリオの利用者数では、すべて記録しても料金は少ない。利用者が増えたら、AWS のおすすめの 0.05（5%）に下げる
  EOT
  type        = number
  default     = 1
  validation {
    condition     = var.trace_sampling_ratio >= 0 && var.trace_sampling_ratio <= 1
    error_message = "trace_sampling_ratio は 0 から 1 の間で指定してください。"
  }
}

variable "db_backup_retention_days" {
  description = <<-EOT
    データベースの自動バックアップを残す日数（1〜35）。ふつうは 7日。
    AWS の無料プラン（Free plan）では、長い日数を指定すると、データベースを作れない（FreeTierRestrictionError）。
    上限の日数は AWS の説明に書かれていないため、無料プランのあいだは terraform.tfvars で 1 にする（AWS の画面の無料利用枠の見本と同じ）。
    有料プランに上げたら、7 に戻す（docs/security.md）
  EOT
  type        = number
  default     = 7
  validation {
    # 0 にすると、自動バックアップが止まるので、1 以上にする
    condition     = var.db_backup_retention_days >= 1 && var.db_backup_retention_days <= 35
    error_message = "db_backup_retention_days は 1 から 35 の間で指定してください。"
  }
}
