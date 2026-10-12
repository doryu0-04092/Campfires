/**
 * 【役割】イベントの作成・編集・削除を、オーナーの画面から確かめるテスト。
 * 【なぜ必要か】イベントの入力欄は多く（日時が3つ、定員、承認の方法など）、間違えやすい。
 * 正しく入れれば作れること、間違えたら分かりやすいエラーが出ることを、本物のブラウザで確かめるため。
 *
 * 確かめる主な機能：
 * - イベント作成の画面（タイトル・説明・開始/終了/締め切りの日時・場所・定員・承認の方法）から作る
 * - 間違った入力（締め切りが開始より後、定員 0、開始が過去）で、エラーが出て作られない
 * - イベント編集の画面で、内容を変える
 * - イベント編集の画面から、イベントを削除する
 *
 * 作ったイベントを、次のテストで編集・削除するので、serial にしている。
 */
import { expect, test, type Page } from '@playwright/test'

import { tag, sessionOf, browserStatePath } from '../../support/accounts'
import { acceptNextDialog, createCommunityViaApi, localDateTime, main } from './helpers'

test.describe.configure({ mode: 'serial' })

// このファイルのテストは、すべてオーナーとしてログインした状態で行う
test.use({ storageState: browserStatePath('owner') })

let communityId: number
let communityName: string
let eventId: number
let title: string
let editedTitle: string

// 【何のため】イベントを作る先のコミュニティを、API で作っておく
test.beforeAll(async () => {
  communityName = `E2E イベント用コミュニティ ${tag()}`
  title = `E2E 画面で作ったイベント ${tag()}`
  editedTitle = `E2E 編集したイベント ${tag()}`
  const owner = await sessionOf('owner')
  try {
    communityId = await createCommunityViaApi(owner, communityName)
  } finally {
    await owner.dispose()
  }
})

/**
 * 【何のため】イベントの入力欄を、まとめて埋める。values で、変えたい項目だけを渡す。
 * Partial<型> は「その型の項目を、全部は書かなくてよい」という意味
 */
async function fillEventForm(
  page: Page,
  values: Partial<{ title: string; startAt: string; endAt: string; deadline: string; capacity: string }> = {},
) {
  await page.getByLabel('タイトル').fill(values.title ?? title)
  await page.getByLabel('説明').fill('画面から作ったイベントの説明です。')
  // datetime-local の入力欄には「2026-10-22T19:00」の形で入れる
  await page.getByLabel('開始日時').fill(values.startAt ?? localDateTime(14, 19))
  await page.getByLabel('終了日時').fill(values.endAt ?? localDateTime(14, 21))
  await page.getByLabel('申込み締め切り日時').fill(values.deadline ?? localDateTime(13, 18))
  await page.getByLabel('場所').fill('E2E 会議室 A')
  await page.getByLabel('定員').fill(values.capacity ?? '10')
}

test('オーナーが、イベント作成の画面からイベントを作れる', async ({ page }) => {
  // 【何のため】入力欄をすべて埋めて作ると、イベントの詳細の画面に、入れた内容が出ることを確かめる
  await page.goto(`/communities/${communityId}`)
  await main(page).getByRole('link', { name: 'イベントを作る' }).click()
  await expect(page).toHaveURL(`/manage/communities/${communityId}/events/new`)
  await expect(page.getByRole('heading', { name: 'イベントを作る' })).toBeVisible()

  await fillEventForm(page)
  // selectOption：選択欄で、見えている文字（label）が同じ選択肢を選ぶ
  await page.getByLabel('コミュニティのメンバーの参加').selectOption({ label: '手動で承認' })
  await page.getByLabel('メンバー以外の参加').selectOption({ label: '自動で承認' })
  await page.getByRole('button', { name: '作成する' }).click()

  // 作ると、そのイベントの詳細の画面に移る。URL の最後の数字が、イベントの番号
  await expect(page).toHaveURL(new RegExp(`/communities/${communityId}/events/\\d+$`))
  eventId = Number(page.url().split('/').pop())
  await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible()
  await expect(main(page).getByText('画面から作ったイベントの説明です。')).toBeVisible()
  await expect(main(page).getByText('E2E 会議室 A')).toBeVisible()
  await expect(main(page).getByText('10人（残り 10人）')).toBeVisible()
  await expect(main(page).getByText('メンバー：手動で承認、メンバー以外：自動で承認')).toBeVisible()
  // オーナーには、管理の画面へのリンクが出る
  await expect(main(page).getByRole('link', { name: 'イベントを編集' })).toBeVisible()
  await expect(main(page).getByRole('link', { name: '申込者一覧' })).toBeVisible()

  // コミュニティの詳細の、開催前のイベントにも出る
  await main(page).getByRole('link', { name: communityName }).click()
  await expect(main(page).getByRole('link', { name: title })).toBeVisible()
})

test('締め切りが開始より後、定員が 0 のときは、エラーが出て作られない', async ({ page }) => {
  // 【何のため】間違った入力では作られず、どこが間違っているかが、入力欄の下に出ることを確かめる
  await page.goto(`/manage/communities/${communityId}/events/new`)
  await fillEventForm(page, {
    title: `E2E 作られないイベント ${tag()}`,
    // 締め切りを、開始（14日後の19時）より後にする
    deadline: localDateTime(14, 20),
    capacity: '0',
  })
  await page.getByRole('button', { name: '作成する' }).click()

  await expect(main(page).getByRole('alert')).toHaveText('入力内容に誤りがあります。各項目の下の説明を確認してください。')
  await expect(main(page).getByText('申込み締め切り日時は、開始日時より前にしてください。')).toBeVisible()
  await expect(main(page).getByText('定員は1〜1000人の整数にしてください。')).toBeVisible()
  await expect(page.getByLabel('申込み締め切り日時')).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByLabel('定員')).toHaveAttribute('aria-invalid', 'true')
  // 作成の画面のまま（イベントの詳細へ移らない）
  await expect(page).toHaveURL(`/manage/communities/${communityId}/events/new`)
})

test('開始日時が過去、終了が開始より前のときも、エラーが出る', async ({ page }) => {
  // 【何のため】日時の前後関係のチェック（今 < 開始 < 終了）が、画面に出ることを確かめる
  await page.goto(`/manage/communities/${communityId}/events/new`)
  await fillEventForm(page, {
    title: `E2E 作られないイベント ${tag()}`,
    startAt: localDateTime(-1, 10),
    endAt: localDateTime(-2, 10),
    deadline: localDateTime(-3, 10),
  })
  await page.getByRole('button', { name: '作成する' }).click()
  await expect(main(page).getByText('開始日時は、今より後にしてください。')).toBeVisible()
  await expect(main(page).getByText('終了日時は、開始日時より後にしてください。')).toBeVisible()
  await expect(page.getByLabel('開始日時')).toHaveAttribute('aria-invalid', 'true')
})

test('オーナーが、イベント編集の画面で内容を変えられる', async ({ page }) => {
  // 【何のため】今の内容が入った状態で編集の画面が開き、変えた内容が保存されることを確かめる
  await page.goto(`/communities/${communityId}/events/${eventId}`)
  await main(page).getByRole('link', { name: 'イベントを編集' }).click()
  await expect(page.getByRole('heading', { name: 'イベントを編集' })).toBeVisible()
  // 今の内容が、入力欄に入っている
  await expect(page.getByLabel('タイトル')).toHaveValue(title)
  await expect(page.getByLabel('定員')).toHaveValue('10')
  await expect(page.getByLabel('開始日時')).toHaveValue(localDateTime(14, 19))
  await expect(page.getByLabel('コミュニティのメンバーの参加')).toHaveValue('manual')

  await page.getByLabel('タイトル').fill(editedTitle)
  await page.getByLabel('定員').fill('20')
  await page.getByLabel('メンバー以外の参加').selectOption({ label: '受け付けない' })
  await page.getByRole('button', { name: '保存する' }).click()

  // 保存すると、イベントの詳細の画面に戻り、新しい内容が出る
  await expect(page).toHaveURL(`/communities/${communityId}/events/${eventId}`)
  await expect(page.getByRole('heading', { name: editedTitle, level: 1 })).toBeVisible()
  await expect(main(page).getByText('20人（残り 20人）')).toBeVisible()
  await expect(main(page).getByText('メンバー：手動で承認、メンバー以外：受け付けない')).toBeVisible()
})

test('オーナーが、イベント編集の画面からイベントを削除できる', async ({ page }) => {
  // 【何のため】確認のダイアログで OK を押すと削除され、コミュニティのイベントの一覧から消えることを確かめる
  await page.goto(`/manage/communities/${communityId}/events/${eventId}/edit`)
  acceptNextDialog(page)
  await page.getByRole('button', { name: 'このイベントを削除する' }).click()

  // 削除すると、コミュニティの詳細の画面に移る
  await expect(page).toHaveURL(`/communities/${communityId}`)
  await expect(main(page).getByText('開催前のイベントはありません。')).toBeVisible()
  // 削除したイベントの URL を開くと「見つかりませんでした」
  await page.goto(`/communities/${communityId}/events/${eventId}`)
  await expect(main(page).getByRole('alert')).toHaveText(/見つかりませんでした/)
})
