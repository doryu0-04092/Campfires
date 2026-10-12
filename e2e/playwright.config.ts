/**
 * 【役割】E2E テスト（Playwright Test）の設定。
 * 【なぜ必要か】いくつかの種類のテストを、同じ準備（テスト用の利用者）で動かすため。
 * - api：画面を使わず、API とデータベースだけを、外から HTTP で確かめる（tests/api）
 * - browser：Microsoft Edge（Chrome と同じ仕組みのブラウザ）で、画面を操作して確かめる（tests/browser）
 * - firefox：同じ画面のテストを、Firefox で確かめる（tests/browser）
 * - webkit：同じ画面のテストを、WebKit（Safari と同じ仕組みのブラウザ）で確かめる（tests/browser）
 * - mobile：スマートフォンの幅（360px）で、主な画面が横にはみ出さないことを確かめる（tests/mobile）
 * - a11y：アクセシビリティ（WCAG 2.2 AA）を、自動の検査（axe）と、キーボードだけの操作で確かめる（tests/a11y）
 *
 * 非機能要件（docs/non-functional-requirements.md）の「対応ブラウザ：Chrome・Edge・Safari・Firefox の最新版」
 * 「スマートフォン（360px）」「WCAG 2.2 AA」を、自動で確かめるために、プロジェクトを分けている。
 *
 * どれも、テストする場所の URL を E2E_BASE_URL で変えられる（support/env.ts）。手元の箱にも、本番にも使える。
 * 使い方は e2e/README.md。
 */
import { defineConfig, devices } from '@playwright/test'

import { BASE_URL } from './support/env'

// パソコンに入っている Microsoft Edge を使う（ブラウザを別にダウンロードしなくてよい）
// E2E_BROWSER_CHANNEL で変えられる（GitHub Actions の Linux にも Edge が入っているので、ふだんは変えなくてよい）
const EDGE_CHANNEL = process.env.E2E_BROWSER_CHANNEL ?? 'msedge'
// パソコンの画面の大きさ（幅 1280px、高さ 800px）
const DESKTOP = { width: 1280, height: 800 }

// アカウントの登録・ログイン・パスワードの変更・削除を画面で確かめるファイル（tests/browser/02-account.spec.ts）と、
// パスワードを忘れた場合の画面を確かめるファイル（tests/browser/10-password.spec.ts）。
// 本番の回数制限（登録は1時間に10回、ログインは10分に20回、再設定の依頼は1時間に5回）に収めるため、Edge（browser）だけで行う。
// Firefox・WebKit では行わない（ログインの画面そのものは、tests/a11y のキーボードのテストでも、Edge で確かめている）
const ACCOUNT_SPEC = ['**/02-account.spec.ts', '**/10-password.spec.ts']

export default defineConfig({
  // テストの準備（利用者を作る）と、後片付け（データと利用者を消す）
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  // テストは1つずつ順番に行う。同じ利用者を使い回すので、同時に動かすと、お互いの結果に影響するため
  workers: 1,
  fullyParallel: false,
  // 1つのテストにかけてよい時間（ミリ秒）
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // 失敗したテストを、もう一度試さない（本番の回数制限に、余計な回数を使わないため）
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    // 失敗したときだけ、画面の記録（トレース）と、画面の写真を残す（原因を調べるため）
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },
  projects: [
    {
      name: 'api',
      testDir: './tests/api',
    },
    {
      // Edge（Chrome と同じ Chromium という仕組みで動くので、Chrome の確認も兼ねる）
      name: 'browser',
      testDir: './tests/browser',
      use: { channel: EDGE_CHANNEL, viewport: DESKTOP },
    },
    {
      name: 'firefox',
      testDir: './tests/browser',
      testIgnore: ACCOUNT_SPEC,
      // devices['Desktop Firefox']：Playwright が用意している「パソコンの Firefox」の設定。
      // ...（スプレッド構文）は「中身を広げて、ここに並べる」という書き方。後ろに書いた viewport で、画面の大きさだけを上書きする
      use: { ...devices['Desktop Firefox'], viewport: DESKTOP },
    },
    {
      // WebKit：Safari と同じ仕組みのブラウザ（Windows や Linux でも動かせる）
      name: 'webkit',
      testDir: './tests/browser',
      testIgnore: ACCOUNT_SPEC,
      use: {
        ...devices['Desktop Safari'],
        viewport: DESKTOP,
        // Windows の WebKit は、画面の記録（トレース）を取ると、とても遅くなる（1つの確認に数秒かかり、時間切れになる）。
        // そのため Windows では記録を取らない（失敗したときの画面の写真は残る）。GitHub Actions の Linux では、ほかと同じく記録を取る
        trace: process.platform === 'win32' ? 'off' : 'retain-on-failure',
      },
    },
    {
      // スマートフォンの画面（幅 360px、高さ 740px。よくある Android のスマートフォンの大きさ）
      name: 'mobile',
      testDir: './tests/mobile',
      use: {
        channel: EDGE_CHANNEL,
        viewport: { width: 360, height: 740 },
        // isMobile：スマートフォンとして画面を作る（meta viewport が効く）。hasTouch：指で触る操作ができる
        isMobile: true,
        hasTouch: true,
        // 1px の点を、画面では2つ分の細かさで描く（スマートフォンの画面と同じ）
        deviceScaleFactor: 2,
      },
    },
    {
      name: 'a11y',
      testDir: './tests/a11y',
      use: { channel: EDGE_CHANNEL, viewport: DESKTOP },
    },
  ],
})
