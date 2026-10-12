# 【役割】本番用の Docker イメージ（アプリの箱）を作る手順（Issue #47）。
# 【なぜ必要か】画面（React）と API（Spring Boot）を1つの箱にまとめ、どこでも同じように動かせるようにするため。
# Spring Boot が、API と一緒に画面のファイルも返すので、画面と API が同じ場所（同じ URL）になり、
# ログインの Cookie と CSRF トークンが、開発中と同じ仕組みで動く。
#
# 作り方（リポジトリのいちばん上のフォルダで）：docker build -t campfires .
# 動かし方の例は、README の「本番用の箱を手元で動かす」を参照。
#
# 3つの段階（ステージ）に分けて作る（マルチステージビルド）。
# 最後の段階には、動かすのに必要なもの（Java の実行環境と、アプリの jar）だけを入れ、箱を小さく、安全にする。

# ============================================================
# 段階1：画面をビルドする（Node.js）
# ============================================================
FROM node:25-alpine AS frontend
WORKDIR /app/frontend
# 先に package.json だけをコピーして、パッケージを入れる。
# ソースだけを変えたときは、この段階の結果を使い回せる（ビルドが速くなる）
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# 本番用の画面のファイルを dist/ に作る。偽の API（MSW）は、本番用のファイルには入らない
RUN npm run build

# ============================================================
# 段階2：API（Spring Boot）をビルドする（Java 21）
# ============================================================
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
# 先に Maven の設定だけをコピーして、必要なライブラリを取ってくる（段階1と同じく、使い回せるようにする）
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY backend/src src
# 画面のファイルを、Spring Boot が返す場所（static/）に入れる
COPY --from=frontend /app/frontend/dist src/main/resources/static
# jar（アプリを1つにまとめたファイル）を作る。自動テストは CI で行うので、ここでは動かさない
RUN ./mvnw -q package -DskipTests

# ============================================================
# 段階3：動かすための箱（Java の実行環境だけ）
# ============================================================
# Alpine Linux 版の Java 21 の実行環境。Ubuntu 版より入っているソフトが少なく、
# 脆弱性の数がいちばん少なかった（Trivy で比べた。docs/security.md を参照）
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# OS のパッケージを、修正済みの最新の版にする（作った時点で直っている脆弱性を残さない）
RUN apk upgrade --no-cache
# 管理者（root）ではない、アプリ専用の利用者で動かす（もし乗っ取られても、できることを少なくするため）
RUN addgroup -S campfires && adduser -S -u 10001 -G campfires campfires
# データベース（AWS の RDS）の証明書を確かめるための、AWS の公開の CA 証明書
# （application-prod.properties の sslmode=verify-full で使う）
ADD --chmod=444 https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /app/rds-ca.pem
# トレースの部品（AWS Distro for OpenTelemetry の Java エージェント）。アプリのコードを変えずに、
# 「リクエストが、API とデータベースのどこで、どれだけ時間を使ったか」を記録し、AWS X-Ray に送る（docs/observability.md）。
# 版を決め（2.31.0）、Maven Central が公開しているファイルの指紋（SHA-256）と一致しなければ、作るのを止める（すり替えの対策）
ADD --checksum=sha256:5cc39bee10a0a3f652333f55378e37aa5f8f6319df97bcd762b41d217bb31709 --chmod=444   https://repo1.maven.org/maven2/software/amazon/opentelemetry/aws-opentelemetry-agent/2.31.0/aws-opentelemetry-agent-2.31.0.jar   /app/aws-opentelemetry-agent.jar
COPY --from=backend /app/backend/target/*.jar app.jar
USER campfires
# 箱の中から、アプリが動いているかを確かめる（Docker の死活確認。AWS ではロードバランサーも確かめる）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/api/health || exit 1
# 本番の設定（application-prod.properties）を使う
ENV SPRING_PROFILES_ACTIVE=prod
# トレースの部品の設定。ふだんは、どこにも送らない（none）。送り先は、動かす場所の環境変数で決める
# （AWS では infra/ecs.tf で X-Ray を、手元では Jaeger を指定する。docs/observability.md）。
# 送らないときも、ログにはトレースの番号（trace_id）が入る
ENV OTEL_SERVICE_NAME=campfires     OTEL_TRACES_EXPORTER=none     OTEL_METRICS_EXPORTER=none     OTEL_LOGS_EXPORTER=none
# アプリが使う入口の番号
EXPOSE 8080
# 箱のメモリの 75% までを、Java が使えるようにする。-javaagent で、トレースの部品を、アプリより先に読み込む
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-javaagent:/app/aws-opentelemetry-agent.jar", "-jar", "app.jar"]
