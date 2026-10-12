# 負荷テスト（k6）

<!--
  【役割】負荷テストの動かし方と、測った結果の記録。
  【なぜ必要か】非機能要件（docs/non-functional-requirements.md）の「性能」の値を守れているかを、同じ手順で何度でも確かめられるようにするため。
-->

本番と同じ Docker イメージを手元で動かし、**本番と同じ大きさ**（アプリ：CPU 0.5・メモリ 1GB、データベース：CPU 2・メモリ 1GB）に制限して測ります。本番の AWS には負荷をかけません（料金と、本物の利用者への影響を避けるため）。

## ファイル

| ファイル | 中身 |
|---|---|
| `seed.sql` | 非機能要件の件数のデータ（ユーザー 1万人、コミュニティ 1,000件、イベント 1万件、申込み 約5万件）を入れる |
| `browse.js` | 100人が同時に、一覧・検索・詳細を見て回る（2分間） |
| `apply.js` | 申込みが1秒間に50件、20秒続く（200人が、定員50人のイベント5つに一斉に申し込む） |

## 動かし方

```bash
# 1. 本番用の箱と、データベースを、本番と同じ大きさで動かす（ネットワーク cf-prod は README の「本番用の箱を手元で動かす」）
docker run -d --name cf-perf-db --network cf-prod --cpus=2 --memory=1g \
  -e POSTGRES_DB=campfires -e POSTGRES_USER=campfires -e POSTGRES_PASSWORD=local-perf-test postgres:17
docker run -d --name cf-perf-app --network cf-prod -p 8083:8080 --cpus=0.5 --memory=1g \
  -e DB_HOST=cf-perf-db -e DB_USERNAME=campfires -e DB_PASSWORD=local-perf-test -e DB_SSL_MODE=disable \
  -e CAMPFIRES_RATE_LIMIT_ENABLED=false campfires

# 2. 負荷テスト用のパスワード（perf-test-password）の BCrypt の値を作るため、1人だけ画面か API で登録し、その値で seed.sql を流す
H=$(docker exec cf-perf-db psql -U campfires -d campfires -tAc "select password_hash from users limit 1")
docker exec -i cf-perf-db psql -q -U campfires -d campfires -v password_hash="$H" < perf/seed.sql

# 3. k6 で負荷をかける（Windows の Git Bash では、先に export MSYS_NO_PATHCONV=1）
docker run --rm --network cf-prod -v "$PWD/perf:/perf" grafana/k6:2.3.0 run -e BASE_URL=http://cf-perf-app:8080 /perf/browse.js
docker run --rm --network cf-prod -v "$PWD/perf:/perf" grafana/k6:2.3.0 run -e BASE_URL=http://cf-perf-app:8080 /perf/apply.js
```

回数制限は止めておきます（負荷テストでは、同じ IP アドレスから大量にログインするため）。

## 結果（2026年10月12日）

| 確かめたこと | 要件 | 結果 |
|---|---|---|
| 100人が同時に使う：API の応答時間（95%） | 0.5秒以内 | **0.04秒**（圧縮を入れる前は 0.20秒） |
| 100人が同時に使う：検索の応答時間（95%） | 0.5秒以内 | **0.06秒**（同 0.27秒） |
| 100人が同時に使う：エラー | ― | 0件（約7,200回） |
| 申込みが1秒間に50件：応答時間（95%） | 0.5秒以内 | **0.06秒** |
| 申込みが1秒間に50件：定員 | 定員を超えない | 5つのイベントとも、**申込み済み 50人（定員ぴったり）**、残りはキャンセル待ち |
| 申込みが1秒間に50件：エラー | ― | 1,000件のうち 1件が 409（データを入れたときに、すでに申し込んでいた組み合わせ。正しい動き） |
| 画面の表示（スマートフォン、遅い回線を想定。Lighthouse の LCP） | 3秒以内（目標 2.5秒） | **1.5〜2.0秒**（圧縮を入れる前は 2.9〜3.3秒で、目標を超えていた） |
| 画面の表示（パソコン） | 同上 | 0.4〜0.5秒 |
| 最初に読み込む JavaScript（圧縮後） | 200KB 以下 | 約120KB |

**直したこと：** 最初の測定で、スマートフォンの表示が目標（2.5秒）を超えていました。原因は、サーバーがファイルを圧縮せずに送り（JavaScript が 400KB のまま）、ブラウザに覚えさせない設定（no-store）だったことです。返事の圧縮（`application.properties` の `server.compression`）と、`/assets/` のファイルを1年間覚えさせる設定（`SpaConfig`）を足しました。API の返事と画面の入り口は、これまでどおり覚えさせません。

Lighthouse の測り方（Edge を使う）：

```bash
CHROME_PATH="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
  npx lighthouse@13.5.0 http://localhost:8083/ --only-categories=performance --chrome-flags="--headless=new"
```
