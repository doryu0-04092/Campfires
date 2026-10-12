/**
 * 【役割】コミュニティの機能を、オーナー・メンバー・メンバー以外の3人の画面から確かめるテスト。
 * 【なぜ必要か】コミュニティは、このアプリの中心。作る・参加する・管理する・やめる、の流れが
 * 画面からつながって使えることを、本物のブラウザで確かめるため。
 *
 * 確かめる主な機能：
 * - オーナーがコミュニティを作る（コミュニティ作成）、名前と説明を変える（コミュニティ編集）
 * - 管理しているコミュニティの一覧に出る
 * - メンバーが検索して参加を申請し、オーナーがメンバー管理の画面で承認する
 * - メンバー以外の人の申請を、オーナーが却下する
 * - オーナーがメンバーを運営スタッフに任命・解除する。運営スタッフには管理の入り口が見える
 * - オーナーがメンバーを除名する
 * - メンバーが自分で退会する
 * - オーナーがアーカイブし、元に戻す
 *
 * 1つのコミュニティで、流れに沿って順番に確かめるので、serial にしている。
 * 3人の画面（ブラウザ）は、ファイルの最初に開き、最後に閉じる。
 */
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'

import { account, tag, sessionOf } from '../../support/accounts'
import { json } from '../../support/api'
import { acceptNextDialog, main, openPageAs } from './helpers'

test.describe.configure({ mode: 'serial' })

// 名前は beforeAll で決める（ファイルを読み込んだ時点では、まだ準備（global-setup）が終わっていないことがあるため）
let firstName: string
let editedName: string
const editedDescription = '編集した説明です。\n2行目もあります。'
// 画面に出るニックネーム
let memberName: string
let outsiderName: string

let communityId: number
// 3人分のブラウザ。Record<キー, 値> は「キーごとに値を1つ持つ表」という意味
const pages = {} as Record<'owner' | 'member' | 'outsider', Page>
const contexts: BrowserContext[] = []

// 【何のため】オーナー・メンバー・メンバー以外の3人のブラウザを開いておく
test.beforeAll(async ({ browser }: { browser: Browser }) => {
  firstName = `E2E 作成コミュニティ ${tag()}`
  editedName = `E2E 編集後コミュニティ ${tag()}`
  memberName = account('member').nickname
  outsiderName = account('outsider').nickname
  for (const role of ['owner', 'member', 'outsider'] as const) {
    const opened = await openPageAs(browser, role)
    contexts.push(opened.context)
    pages[role] = opened.page
  }
})

// 【何のため】最後に、3人のブラウザを閉じる
test.afterAll(async () => {
  for (const context of contexts) await context.close()
})

/** 【何のため】メンバー管理の画面の、見出しごとのまとまり（「参加申請」「オーナー・運営スタッフ」「メンバー」） */
function section(page: Page, title: string) {
  // has：中に、この見出しを持つ div を探す
  return main(page).locator('div', { has: page.getByRole('heading', { name: title, exact: true, level: 2 }) })
}

/** 【何のため】まとまりの中から、その人の行（カード）を探す */
function row(page: Page, title: string, nickname: string) {
  return section(page, title).locator('li', { hasText: nickname })
}

test('オーナーが、コミュニティ作成の画面からコミュニティを作れる', async () => {
  // 【何のため】名前と説明を入れて作ると、管理しているコミュニティの一覧に、オーナーとして出ることを確かめる
  const page = pages.owner
  await page.goto('/manage/communities/new')
  await expect(page.getByRole('heading', { name: 'コミュニティを作る' })).toBeVisible()
  await page.getByLabel('コミュニティの名前').fill(firstName)
  await page.getByLabel('説明').fill('最初の説明です。')
  await page.getByRole('button', { name: '作成する' }).click()

  // 作ると、管理しているコミュニティの一覧に移る
  await expect(page).toHaveURL('/manage/communities')
  const card = main(page).locator('li', { hasText: firstName })
  await expect(card).toBeVisible()
  await expect(card.getByText('オーナー', { exact: true })).toBeVisible()
  await expect(card.getByText('メンバー 1人')).toBeVisible()

  // リンクの URL（/communities/123）から、コミュニティの番号を取り出す
  const href = await card.getByRole('link', { name: firstName }).getAttribute('href')
  communityId = Number(href!.split('/').pop())
  expect(communityId).toBeGreaterThan(0)
})

test('名前を空にして保存すると、エラーが出る', async () => {
  // 【何のため】入力のチェックが画面に出ることを確かめる
  const page = pages.owner
  await page.goto(`/manage/communities/${communityId}/edit`)
  await expect(page.getByRole('heading', { name: 'コミュニティを編集' })).toBeVisible()
  await page.getByLabel('コミュニティの名前').fill('')
  await page.getByRole('button', { name: '保存する' }).click()
  await expect(page.getByLabel('コミュニティの名前')).toHaveAttribute('aria-invalid', 'true')
})

test('オーナーが、コミュニティの名前と説明を変えられる', async () => {
  // 【何のため】編集した内容が保存され、詳細の画面にも出ることを確かめる
  const page = pages.owner
  await page.goto(`/manage/communities/${communityId}/edit`)
  await page.getByLabel('コミュニティの名前').fill(editedName)
  await page.getByLabel('説明').fill(editedDescription)
  await page.getByRole('button', { name: '保存する' }).click()
  await expect(main(page).getByRole('status')).toHaveText('保存しました。')

  // 詳細の画面へ戻ると、新しい名前と説明が出る
  await main(page).getByRole('link', { name: `「${editedName}」の詳細へ戻る` }).click()
  await expect(page.getByRole('heading', { name: editedName, level: 1 })).toBeVisible()
  await expect(main(page).getByText('2行目もあります。')).toBeVisible()
  await expect(main(page).getByText('あなたはこのコミュニティのオーナーです。')).toBeVisible()
  // オーナーには、管理の画面へのリンクが出る
  await expect(main(page).getByRole('link', { name: 'コミュニティを編集' })).toBeVisible()
  await expect(main(page).getByRole('link', { name: 'メンバー管理' })).toBeVisible()
  await expect(main(page).getByRole('link', { name: 'イベントを作る' })).toBeVisible()

  // 管理しているコミュニティの一覧にも、新しい名前で出る
  await page.goto('/manage/communities')
  await expect(main(page).getByRole('link', { name: editedName })).toBeVisible()
})

test('メンバーとメンバー以外の人が、検索して参加を申請できる', async () => {
  // 【何のため】コミュニティを探して、参加を申請する流れを確かめる
  for (const role of ['member', 'outsider'] as const) {
    const page = pages[role]
    await page.goto('/communities')
    await page.getByLabel('コミュニティを検索').fill(editedName)
    await page.getByRole('button', { name: '検索' }).click()
    await main(page).getByRole('link', { name: editedName }).click()
    await expect(page).toHaveURL(`/communities/${communityId}`)

    await main(page).getByRole('button', { name: '参加を申請する' }).click()
    await expect(main(page).getByText('参加を申請しています。オーナーか運営スタッフの承認を待っています。')).toBeVisible()
  }
})

test('オーナーが、メンバー管理の画面で、申請を承認・却下できる', async () => {
  // 【何のため】参加申請が届き、承認した人はメンバーになり、却下した人はメンバーにならないことを確かめる
  const page = pages.owner
  await page.goto('/manage/communities')
  const card = main(page).locator('li', { hasText: editedName })
  // 承認を待っている申請の数が、一覧で目立つように出る
  await expect(card.getByText('承認待ち 2件')).toBeVisible()
  await card.getByRole('link', { name: 'メンバー管理' }).click()
  await expect(page.getByRole('heading', { name: `メンバー管理：${editedName}` })).toBeVisible()
  await expect(section(page, '参加申請').getByText('承認を待っている申請：2件')).toBeVisible()

  // メンバーの申請を承認する → 「メンバー」の枠に出る
  await row(page, '参加申請', memberName).getByRole('button', { name: '承認する' }).click()
  await expect(row(page, 'メンバー', memberName)).toBeVisible()
  await expect(row(page, 'メンバー', memberName).getByText('メンバー', { exact: true })).toBeVisible()

  // メンバー以外の人の申請を却下する → 申請の一覧から消え、メンバーにもならない
  await row(page, '参加申請', outsiderName).getByRole('button', { name: '却下する' }).click()
  await expect(section(page, '参加申請').getByText('承認を待っている参加申請はありません。')).toBeVisible()
  await expect(row(page, 'メンバー', outsiderName)).toHaveCount(0)
})

test('承認された人はメンバーになり、却下された人には、もう一度申請できると出る', async () => {
  // 【何のため】申請した側の画面で、結果が分かることを確かめる
  await pages.member.goto(`/communities/${communityId}`)
  await expect(main(pages.member).getByText('あなたの立場：メンバー')).toBeVisible()
  await expect(main(pages.member).getByRole('button', { name: 'このコミュニティを退会する' })).toBeVisible()

  await pages.outsider.goto(`/communities/${communityId}`)
  await expect(main(pages.outsider).getByText('前回の参加の申請は、承認されませんでした。もう一度申請できます。')).toBeVisible()
  await expect(main(pages.outsider).getByRole('button', { name: '参加を申請する' })).toBeVisible()
})

test('オーナーが、メンバーを運営スタッフに任命できる', async () => {
  // 【何のため】任命すると、その人が「オーナー・運営スタッフ」の枠に移ることを確かめる
  const page = pages.owner
  await page.goto(`/manage/communities/${communityId}/members`)
  await row(page, 'メンバー', memberName).getByRole('button', { name: '運営スタッフに任命する' }).click()
  const staffRow = row(page, 'オーナー・運営スタッフ', memberName)
  await expect(staffRow).toBeVisible()
  await expect(staffRow.getByText('運営スタッフ', { exact: true })).toBeVisible()
  await expect(section(page, 'メンバー').getByText('一般のメンバーは、まだいません。')).toBeVisible()
})

test('運営スタッフには、管理の入り口が見えるが、オーナーだけの操作はできない', async () => {
  // 【何のため】運営スタッフが使える管理の機能（メンバー管理・イベント作成）と、使えない機能（編集・任命）を確かめる
  const page = pages.member
  await page.goto(`/communities/${communityId}`)
  await expect(main(page).getByText('あなたの立場：運営スタッフ')).toBeVisible()
  await expect(main(page).getByRole('link', { name: 'メンバー管理' })).toBeVisible()
  await expect(main(page).getByRole('link', { name: 'イベントを作る' })).toBeVisible()
  // コミュニティの編集は、オーナーだけ
  await expect(main(page).getByRole('link', { name: 'コミュニティを編集' })).toHaveCount(0)

  // 左のメニューの「メンバー管理」から選ぶ画面に、このコミュニティが出る
  await page.goto('/manage/select/members')
  await expect(main(page).getByRole('link', { name: editedName })).toBeVisible()
  // 「コミュニティ編集」の選ぶ画面には出ない（オーナーではないので）
  await page.goto('/manage/select/community-edit')
  await expect(page.getByRole('heading', { name: 'コミュニティ編集：コミュニティを選ぶ' })).toBeVisible()
  await expect(main(page).getByRole('link', { name: editedName })).toHaveCount(0)

  // メンバー管理の画面は開けるが、任命・除名のボタンは出ない
  await page.goto(`/manage/communities/${communityId}/members`)
  await expect(page.getByRole('heading', { name: `メンバー管理：${editedName}` })).toBeVisible()
  await expect(row(page, 'オーナー・運営スタッフ', memberName)).toBeVisible()
  await expect(main(page).getByRole('button', { name: /運営スタッフを解除する|除名する/ })).toHaveCount(0)

  // 編集の画面を URL で直接開いても、オーナーだけと出る
  await page.goto(`/manage/communities/${communityId}/edit`)
  await expect(main(page).getByText('コミュニティを編集できるのは、オーナーだけです。')).toBeVisible()
})

test('オーナーが、運営スタッフを解除できる', async () => {
  // 【何のため】解除すると、その人が一般の「メンバー」の枠に戻ることを確かめる
  const page = pages.owner
  await page.goto(`/manage/communities/${communityId}/members`)
  await row(page, 'オーナー・運営スタッフ', memberName).getByRole('button', { name: '運営スタッフを解除する' }).click()
  await expect(row(page, 'メンバー', memberName).getByRole('button', { name: '運営スタッフに任命する' })).toBeVisible()
  await expect(row(page, 'オーナー・運営スタッフ', memberName)).toHaveCount(0)
})

test('オーナーが、メンバーを除名できる', async () => {
  // 【何のため】確認のダイアログで OK を押すと除名され、除名された人はメンバーでなくなることを確かめる
  const page = pages.owner
  acceptNextDialog(page)
  await row(page, 'メンバー', memberName).getByRole('button', { name: '除名する' }).click()
  await expect(section(page, 'メンバー').getByText('一般のメンバーは、まだいません。')).toBeVisible()

  await pages.member.goto(`/communities/${communityId}`)
  await expect(main(pages.member).getByRole('button', { name: '参加を申請する' })).toBeVisible()
  await expect(main(pages.member).getByText('あなたの立場：')).toHaveCount(0)
})

test('メンバーが、自分でコミュニティを退会できる', async () => {
  // 【何のため】確認のダイアログで OK を押すと退会でき、メンバーでなくなることを確かめる
  // 準備：もう一度メンバーにする（申請は画面から、承認は API で）
  const page = pages.member
  await main(page).getByRole('button', { name: '参加を申請する' }).click()
  await expect(main(page).getByText('参加を申請しています。')).toBeVisible()
  const owner = await sessionOf('owner')
  try {
    const requests = await json<{ items: { id: number; user: { nickname: string } }[] }>(
      await owner.get(`/api/communities/${communityId}/join-requests`),
    )
    // find：条件に合う最初の1つを探す
    const request = requests.items.find((r) => r.user.nickname === memberName)!
    await json(await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'approved' }))
  } finally {
    await owner.dispose()
  }

  await page.reload()
  await expect(main(page).getByText('あなたの立場：メンバー')).toBeVisible()
  acceptNextDialog(page)
  await main(page).getByRole('button', { name: 'このコミュニティを退会する' }).click()
  await expect(main(page).getByRole('button', { name: '参加を申請する' })).toBeVisible()
  await expect(main(page).getByText('あなたの立場：')).toHaveCount(0)
})

test('オーナーがアーカイブすると、編集も申請もできなくなり、検索にも出ない', async () => {
  // 【何のため】活動を終えたコミュニティを、削除の代わりにアーカイブできることを確かめる
  const page = pages.owner
  await page.goto(`/manage/communities/${communityId}/edit`)
  acceptNextDialog(page)
  await page.getByRole('button', { name: 'アーカイブする' }).click()
  await expect(main(page).getByText('このコミュニティはアーカイブされています。')).toBeVisible()
  // アーカイブ中は、名前と説明を編集できない
  await expect(page.getByLabel('コミュニティの名前')).toBeDisabled()
  await expect(page.getByRole('button', { name: '保存する' })).toBeDisabled()

  // メンバー以外の人の画面：知らせが出て、参加の申請のボタンが出ない
  const outsider = pages.outsider
  await outsider.goto(`/communities/${communityId}`)
  await expect(main(outsider).getByText(/このコミュニティはアーカイブされています/)).toBeVisible()
  await expect(main(outsider).getByRole('button', { name: '参加を申請する' })).toHaveCount(0)
  // 検索にも出ない。検索は「どれかの単語に当てはまるもの」を出すので、ほかのコミュニティは出ることがある。
  // そのため「このコミュニティのリンクがない」ことを確かめる。
  // encodeURIComponent：日本語や空白を、URL に入れられる形に変える
  await outsider.goto(`/communities?q=${encodeURIComponent(editedName)}`)
  await expect(main(outsider).getByText(/検索結果：\d+件見つかりました/)).toBeVisible()
  await expect(main(outsider).getByRole('link', { name: editedName })).toHaveCount(0)
})

test('オーナーが、管理しているコミュニティの一覧から、アーカイブを元に戻せる', async () => {
  // 【何のため】アーカイブしたコミュニティを、あとで元に戻せることを確かめる
  const page = pages.owner
  await page.goto('/manage/communities')
  const card = main(page).locator('li', { hasText: editedName })
  await expect(card.getByText('アーカイブ済み')).toBeVisible()
  // アーカイブ中は「イベントを作る」が出ない
  await expect(card.getByRole('link', { name: 'イベントを作る' })).toHaveCount(0)
  await card.getByRole('button', { name: '元に戻す' }).click()
  await expect(card.getByText('アーカイブ済み')).toHaveCount(0)
  await expect(card.getByRole('link', { name: 'イベントを作る' })).toBeVisible()

  // 元に戻すと、また申請できる
  await pages.outsider.goto(`/communities/${communityId}`)
  await expect(main(pages.outsider).getByRole('button', { name: '参加を申請する' })).toBeVisible()
})
