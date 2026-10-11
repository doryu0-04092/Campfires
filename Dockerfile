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
FROM node:24-alpine AS frontend
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
COPY --from=backend /app/backend/target/*.jar app.jar
USER campfires
# 箱の中から、アプリが動いているかを確かめる（Docker の死活確認。AWS ではロードバランサーも確かめる）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/api/health || exit 1
# 本番の設定（application-prod.properties）を使う
ENV SPRING_PROFILES_ACTIVE=prod
# アプリが使う入口の番号
EXPOSE 8080
# 箱のメモリの 75% までを、Java が使えるようにする
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
