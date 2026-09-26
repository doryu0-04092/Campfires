# frontend（画面）

Campfires の画面です。React と TypeScript で作り、開発ツールに Vite を使います。

## 使い方

`frontend` フォルダでターミナルを開き、次のコマンドを実行します。

| コマンド | 内容 |
|---|---|
| `npm install` | 必要なパッケージをインストールする。最初に1回だけ実行する |
| `npm run dev` | 開発用サーバーを起動する。表示された URL（http://localhost:5173）をブラウザで開く |
| `npm run build` | 型をチェックしてから、本番用のファイルを `dist` フォルダに作る |
| `npm run lint` | 書き方のチェックを行う |
| `npm run preview` | `npm run build` で作ったファイルを、ブラウザで確認する |

## ファイルの役割

| ファイル | 役割 |
|---|---|
| `index.html` | ブラウザが最初に読み込む HTML。画面の入れ物 |
| `src/main.tsx` | 画面全体の入り口。React の画面を `index.html` の中に表示する |
| `src/App.tsx` | 画面のいちばん外側の部品 |
| `src/index.css` | アプリ全体に共通する見た目 |
| `vite.config.ts` | 開発ツール Vite の設定 |
| `tsconfig.json` ほか | TypeScript のチェックの設定 |
| `.oxlintrc.json` | 書き方のチェック（Oxlint）の設定 |
| `.gitignore` | Git で管理しないファイルの一覧 |
| `package.json` | 使うパッケージとコマンドの一覧（下で説明） |
| `package-lock.json` | インストールしたパッケージの正確なバージョンの記録。自動で作られるので手で編集しない |

## package.json の説明

`package.json` は JSON という形式で、コメントを書けません。そのため、ここで説明します。

### scripts（コマンド）

| 名前 | 実行される内容 | 意味 |
|---|---|---|
| `dev` | `vite` | 開発用サーバーを起動する |
| `build` | `tsc -b && vite build` | TypeScript でチェックし、問題がなければ本番用ファイルを作る |
| `lint` | `oxlint` | 書き方をチェックする |
| `preview` | `vite preview` | 本番用ファイルを確認する |

### dependencies（アプリの動作に必要なパッケージ）

| パッケージ | 役割 |
|---|---|
| `react` | 画面を部品の組み合わせで作るライブラリ |
| `react-dom` | React の画面をブラウザに表示する |

### devDependencies（開発のときだけ使うパッケージ）

| パッケージ | 役割 |
|---|---|
| `vite` | 開発用サーバーと、本番用ファイルの作成 |
| `@vitejs/plugin-react` | Vite で React を使えるようにする |
| `typescript` | TypeScript のチェック |
| `@types/react`、`@types/react-dom`、`@types/node` | React と Node.js の型の情報 |
| `oxlint` | 書き方のチェック |

パッケージを追加したときは、この表にも追加します。
