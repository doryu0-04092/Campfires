# E2E テスト（Playwright Test）

<!--
  【役割】E2E テスト（アプリ全体を、外から本物の通り道で確かめるテスト）の動かし方と、決まりの説明。
  【なぜ必要か】同じテストを、手元の箱にも本番にも使う。本番で動かすときに、本物の利用者に影響しないための決まりを残すため。
-->

## テストの種類（プロジェクト）

| プロジェクト | 場所 | 中身 |
|---|---|---|
| `api` | `tests/api/` | 画面を使わず、API とデータベースだけを、外から HTTP で確かめる |
| `browser` | `tests/browser/` | Microsoft Edge で、画面を操作して確かめる（主な機能のすべて）。Edge は Chrome と同じ仕組み（Chromium）なので、Chrome の確認も兼ねる |
| `firefox` | `tests/browser/` | 同じ画面のテストを Firefox で行う（`02-account.spec.ts`・`10-password.spec.ts` は除く。下の「回数制限」を参照） |
| `webkit` | `tests/browser/` | 同じ画面のテストを WebKit（Safari と同じ仕組み）で行う（`02-account.spec.ts`・`10-password.spec.ts` は除く） |
| `mobile` | `tests/mobile/` | スマートフォンの幅（360×740）で、主な画面が横にはみ出さないこと、メニューを指で使えることを確かめる |
| `a11y` | `tests/a11y/` | アクセシビリティ（WCAG 2.2 AA）を、自動の検査（axe）と、キーボードだけの操作で確かめる（Edge で行う） |

非機能要件（`docs/non-functional-requirements.md`）の「対応ブラウザ（Chrome・Edge・Safari・Firefox）」「スマートフォン（360px）」「WCAG 2.2 AA」を、自動で確かめるためのプロジェクトです。
`mobile` と `a11y` で開く「主な画面」の一覧は、`support/screens.ts` にまとめています。

バックエンドの中だけで確かめるテスト（単体テスト・結合テスト）は、`backend/` の `./mvnw test` です。

## 動かし方

```bash
cd e2e
npm ci
# Firefox と WebKit を、Playwright に入れる（最初の1回だけ。Edge は、パソコンに入っているものを使う）
npm run install:browsers

# 手元の本番用の箱（http://localhost:8081）を、すべてテストする
npm test

# API だけ、Edge だけ
npm run test:api
npm run test:browser
# Edge・Firefox・WebKit の3つ、スマートフォンの幅、アクセシビリティ
npm run test:browsers
npm run test:mobile
npm run test:a11y

# 本番をテストする（URL を変えるだけ）
E2E_BASE_URL=https://（公開した URL） npm test

# 結果の画面（失敗したテストの、画面の写真と記録を見られる）
npm run report
```

手元の本番用の箱の動かし方は、リポジトリの README の「本番用の箱を手元で動かす」を参照してください。

## 本番で動かしても、本物の利用者に影響しないための決まり

1. **データは、テストが自分で作り、最後に消す。** 本番には開発用のデータがありません。準備（`global-setup.ts`）でテスト用の利用者を3人登録し、後片付け（`global-teardown.ts`）で、作ったイベントを削除し、コミュニティをアーカイブし、退会します。テストが途中で失敗しても、後片付けは行います。
2. **テストのデータには、実行ごとの番号を付ける。** メールアドレスは `e2e-（番号）-owner@example.com` の形です（`example.com` は、例として使うために決められたドメインで、本物のメールは届きません）。パスワードは、実行ごとにランダムに作ります。
3. **本番の回数制限に収まるようにする。** 本番では、同じ IP アドレスから、アカウントの登録は 1時間に 10回、ログインは 10分間に 20回、パスワードの再設定の依頼は 1時間に 5回までです（失敗した登録・ログイン・依頼も数えます）。すべてのプロジェクトを1回動かしたときの回数は、下の「回数制限の内訳」のとおりです。**本番での実行は、1時間に2回まで**、手元の本番用の箱（http://localhost:8081）では **10分間に1回まで、かつ1時間に5回まで**（再設定の依頼の上限のため）にしてください。
4. **本物の利用者に影響するテストは、手元だけで行う。** ログインのロック（5回の失敗）は `tests/api/local-only.spec.ts` にあり、本番の URL のときは行いません。
5. **テストの利用者の情報は、Git に入れない。** パスワードとログインの Cookie は `.auth/` に置きます（`.gitignore`）。

退会したテスト用の利用者は、データベースに「退会したユーザー」として行だけが残ります（アプリの決まりで、過去の申込みの記録のため）。名前とメールアドレスは消えます。

### 回数制限の内訳（すべてのプロジェクトを1回動かしたとき）

| どこで | 登録 | ログイン | 再設定の依頼 |
|---|---|---|---|
| 準備（`global-setup.ts`）：owner・member・outsider を登録 | 3 | 0 | 0 |
| `api`：`account.spec.ts`（ログインの失敗と成功、登録済みのメールアドレスでの登録） | 1 | 2 | 0 |
| `api`：`password.spec.ts`（パスワードの変更で「ほかの端末」を作るログイン、再設定を止めていることの確認） | 0 | 1 | 1 |
| `api`：`platform.spec.ts`（ログインの Cookie の守り） | 0 | 1 | 0 |
| `api`：`local-only.spec.ts`（ログインのロック。**手元の箱だけ**） | 0 | 6 | 0 |
| `browser`：`02-account.spec.ts`（画面で登録、パスワードの間違い、ログイン、パスワードの変更） | 1 | 2 | 0 |
| `browser`：`10-password.spec.ts`（パスワードを忘れた場合の画面。ログインせず、依頼もしない） | 0 | 0 | 0 |
| `firefox`・`webkit`・`mobile`：準備で保存したログインの Cookie を使う | 0 | 0 | 0 |
| `a11y`：`02-keyboard.spec.ts`（キーボードだけでログイン） | 0 | 1 | 0 |
| 後片付け（`global-teardown.ts`）：保存したログインの Cookie を使う | 0 | 0（Cookie が切れていたときだけ、最大 3） | 0 |
| **合計（手元の箱）** | **5**（上限 10／1時間） | **13**（上限 20／10分間。最悪でも 16） | **1**（上限 5／1時間） |
| **合計（本番。`local-only` は行わない）** | **5** | **7**（最悪でも 10） | **1** |

パスワードの変更のテスト（`password.spec.ts`）は、outsider のパスワードを一度変えて、元に戻します。パスワードを変えると、それまでのログインの Cookie はすべて使えなくなるので、戻したあとの新しい Cookie を `.auth/` に保存し直します（あとの画面のテストと後片付けは、その Cookie を使います）。画面のパスワードの変更は、`02-account.spec.ts` で登録した利用者（extra）で行います。
パスワードの再設定は、本番にメールを送る仕組みがまだないので止めています（`503`）。テストは「止まっていること」を確かめます。再設定を使えるようにしたら、`password.spec.ts` と `10-password.spec.ts` を書き直してください。

回数を少なくするための工夫：

- 画面のテストは、準備で保存したログインの Cookie（`.auth/〜.browser.json`）で始める。画面でログインするのは、`02-account.spec.ts` とキーボードのテストだけ。
- `02-account.spec.ts`（登録・ログイン・パスワードの変更・退会の画面）と `10-password.spec.ts`（パスワードを忘れた場合の画面）は、Edge（`browser`）だけで行う。Firefox・WebKit でも行うと、1回の実行で登録が 7回、ログインが 16回になり、本番で1時間に2回動かせなくなるため（`playwright.config.ts` の `testIgnore`）。
- 後片付けは、まず保存したログインの Cookie を使い、入れないときだけログインし直す。画面のテストで退会した利用者は、一覧から消しておき、後片付けでログインしない。

回数は、決まった時間の区切り（登録は毎時0分から1時間、ログインは10分ごと）で数え直されます。`429`（回数制限）で失敗したときは、次の区切りまで待ってから動かしてください。

## ブラウザごとの注意

- **WebKit と、手元の箱（http）の Cookie。** ログインの Cookie には `secure`（HTTPS のときだけ送る）の印が付いています。WebKit は、http://localhost ではこの Cookie を送らないため、手元の箱をテストするときだけ、画面のテスト用の Cookie（`.auth/〜.browser.json`）から、この印を外しています（`support/accounts.ts` の `saveSession`）。本番（https）では外しません。API のテストは、サーバーから受け取ったままの Cookie（`.auth/〜.json`）を使います。
- **Windows の WebKit は、トレース（画面の記録）を取ると、とても遅くなる**ため、Windows では記録を取りません（失敗したときの画面の写真は残ります）。
- **プロジェクトごとに、データの名前を変える。** 同じ画面のテストを3つのブラウザで続けて行うので、データの名前に、実行ごとの番号とプロジェクトの名前の先頭2文字を付けます（例：`E2E 公開コミュニティ mv54m63l-fi`。`support/accounts.ts` の `tag`）。

## GitHub Actions で動かすとき

Firefox と WebKit は、Playwright が用意したものを入れます（Linux に必要な部品も一緒に入れる `--with-deps` を付けます）。Edge は、GitHub Actions の Ubuntu に、最初から入っています。

```bash
cd e2e
npm ci
npx playwright install --with-deps firefox webkit
npm test
```

## 2つの実行を同時に動かすとき

後片付けは、`.auth/accounts.json` に書いた利用者を消します。同時に動かすときは、`E2E_AUTH_DIR=.auth-2` のように、別のフォルダを指定してください。
