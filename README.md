# Campfires
勉強会・コミュニティ運営のためのイベント申込み管理アプリ

## フォルダ

| フォルダ | 中身 | 説明 |
|---|---|---|
| `frontend` | 画面（React、TypeScript、Vite） | [frontend/README.md](frontend/README.md) |
| `backend` | API（Java、Spring Boot、MyBatis、PostgreSQL） | [backend/README.md](backend/README.md) |
| `docs` | 要件定義書、画面一覧、API 仕様書など | |

## 動かし方

画面だけを動かす方法と、画面と API（バックエンド）を一緒に動かす方法があります。

### 画面だけ（偽の API を使う）

```
cd frontend
npm install
npm run dev
```

http://localhost:5173 を開きます。偽の API（MSW）がブラウザの中で返事をするので、Java や Docker がなくても動きます。

### 画面と API を一緒に（本物の API を使う）

Java 21 と Docker Desktop が必要です。ターミナルを2つ開きます。

```
# 1つ目：API（http://localhost:8080）。PostgreSQL も Docker で自動で起動する
cd backend
./mvnw spring-boot:run

# 2つ目：画面（http://localhost:5173）。/api への通信を、1つ目の API に転送する
cd frontend
npm run dev:api
```

http://localhost:5173 を開きます。どちらの方法でも、同じ開発用のアカウント（[frontend/README.md](frontend/README.md) の「開発用のアカウント」）でログインできます。
