# 【役割】ECS Express Mode が作ったロードバランサー（ALB）に、HTTP（80番）の入口を足し、HTTPS へ移動させる設定。
# 【なぜ必要か】非機能要件（docs/non-functional-requirements.md）の「HTTP で来たアクセスは HTTPS に移動させる」を満たすため。
# Express Mode が作る ALB には、HTTPS（443番）の入口しかなく、http:// で開くと、つながらなかった。
#
# AWS の説明（Updating Resources Outside of Express Mode）：Express Mode が作ったものも、自分で直接変えてよい。
# 変えたものは残り、Express Mode の更新で、その部分を変えるように頼まない限り、上書きされない。
# ALB のセキュリティグループは、Express Mode が最初から 80番も許しているので、入口（リスナー）だけを足す。

# Express Mode が作った ALB を、タグで探す（このアプリのタグと、Express Mode の印の両方が付いたもの）
data "aws_lb" "express" {
  tags = {
    AmazonECSManaged = "true"
    Project          = var.app_name
  }
  # アプリ（と ALB）を作り終わってから探す（新しく作るときに、まだ ALB がない状態で探さないように）
  depends_on = [aws_ecs_express_gateway_service.app]
}

# HTTP（80番）の入口：来たリクエストを、同じ場所の HTTPS の URL へ移動させる（301：ずっと移動した、という知らせ）
resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = data.aws_lb.express.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      protocol    = "HTTPS"
      port        = "443"
      status_code = "HTTP_301"
      # ホスト名、パス、? より後ろは、そのまま引き継ぐ（書かなければ、AWS が元の値を使う）
    }
  }
}
