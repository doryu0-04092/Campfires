# デモ動画の撮り方

<!--
  【役割】README に載せているデモ動画（約2分）の撮り方の手順。
  【なぜ必要か】画面が変わったときに、同じ流れの動画を、同じ手順で撮り直せるようにするため。
-->

| ファイル | 中身 |
|---|---|
| `seed.mjs` | 見本のデータ（ゆうき・さくらたちと、コミュニティ、イベント）を、API で入れる |
| `record.mjs` | Edge を自動で操作して録画する（カーソル・強調の枠・字幕・案内・最後の説明の画面を描く） |

## 動画の流れ

1. さくら（まだログインしていない）が、ゆうきがオーナーのコミュニティのイベント「Java 21 の新機能を読む会」を見つける
2. さくらが「申し込む」→ ログイン → 元のイベントの画面に戻る → 申し込む →「承認待ち」になる
3. オーナーのゆうきが、申込者一覧で、さくらの申込みを承認する → さくらは「申込み済み」
4. ゆうきが、新しいイベントを作る
5. 最後に、画面の構造・今回操作した流れ・アプリの仕組みを1枚で説明する

## 撮り方

本番には見本のデータを入れないので、本番と同じイメージの箱を、手元に空のデータベースで立てて撮ります（Windows の Git Bash では、先に `export MSYS_NO_PATHCONV=1`）。

```bash
# 1. 録画用の箱（空のデータベースと、本番と同じイメージ。回数制限は止める）
docker network create campfires-demo
docker run -d --name demo-db --network campfires-demo \
  -e POSTGRES_DB=campfires -e POSTGRES_USER=campfires -e POSTGRES_PASSWORD=local-demo postgres:17
docker run -d --name demo-app --network campfires-demo -p 8084:8080 \
  -e DB_HOST=demo-db -e DB_USERNAME=campfires -e DB_PASSWORD=local-demo -e DB_SSL_MODE=disable \
  -e CAMPFIRES_RATE_LIMIT_ENABLED=false campfires

# 2. 見本のデータを入れて、録画する（e2e フォルダで。Playwright の部品を使う）
cd e2e
node demo/seed.mjs
npm run demo:record          # demo-output/ に webm の動画ができる（2分ほどかかる）

# 3. GitHub で再生できる mp4（H.264）に変える
docker run --rm -v "$PWD/demo-output:/v" jrottenberg/ffmpeg:7.1-ubuntu -i /v/（できた動画）.webm \
  -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -movflags +faststart -an /v/campfires-demo.mp4
```

もう一度撮るときは、`demo-db` と `demo-app` を消して、1 からやり直します（データが、見本の状態に戻る）。

## README に載せる

GitHub の README で再生できる動画は、10MB までです（この動画は 2.5MB ほど）。
mp4 を、GitHub の PR やイシューのコメント欄にドラッグ＆ドロップすると、`https://github.com/user-attachments/assets/…` の URL ができます。その URL を、README に1行で書くと、動画のプレーヤーとして表示されます。
