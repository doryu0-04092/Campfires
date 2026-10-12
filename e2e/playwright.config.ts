/**
 * 【役割】E2E テスト（Playwright Test）の設定。
 * 【なぜ必要か】2種類のテストを、同じ準備（テスト用の利用者）で動かすため。
 * - api：画面を使わず、API とデータベースだけを、外から HTTP で確かめる（tests/api）
 * - browser：本物のブラウザ（Microsoft Edge）で、画面を操作して確かめる（tests/browser）
 *
 * どちらも、テストする場所の URL を E2E_BASE_URL で変えられる（support/env.ts）。手元の箱にも、本番にも使える。
 * 使い方は e2e/README.md。
 */
import { defineConfig } from '@playwright/test'

import { BASE_URL } from './support/env'

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
      name: 'browser',
      testDir: './tests/browser',
      // パソコンに入っている Microsoft Edge を使う（ブラウザを別にダウンロードしなくてよい）
      // E2E_BROWSER_CHANNEL で変えられる（GitHub Actions の Linux にも Edge が入っているので、ふだんは変えなくてよい）
      use: { channel: process.env.E2E_BROWSER_CHANNEL ?? 'msedge', viewport: { width: 1280, height: 800 } },
    },
  ],
})
