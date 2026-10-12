/**
 * 【役割】スマートフォンの表示のテスト（tests/mobile）と、アクセシビリティのテスト（tests/a11y）で、
 * 開いて確かめる「主な画面」の一覧と、その画面に出すデータの準備をまとめたファイル。
 * 【なぜ必要か】2つのテストで、同じ画面を同じように開くので、一覧を1か所にまとめて、
 * 画面を増やしたときに、片方だけ直し忘れることがないようにするため。
 *
 * データ（コミュニティ・イベント・参加申請・申込み）は、画面ではなく API で作る（速く・確実に済ませるため）。
 * このファイルの名前は .spec.ts で終わらないので、Playwright はテストとしては実行しない（部品として読み込むだけ）。
 */
import { expect, type Page } from '@playwright/test'

import { sessionOf, tag, type Role } from './accounts'
import { eventInput, json, type ApiSession } from './api'

/** 主な画面の1つ */
export interface Screen {
  /** テストの名前に使う、画面の名前 */
  name: string
  /** 開く URL */
  path: string
  /** 誰として開くか（guest：ログインしていない人） */
  as: Role | 'guest'
  /** 開いたときに出る、いちばん大きな見出し（h1）。これが出たら、画面が表示されたとみなす */
  heading: string
  /** 中身（データ）が読み込まれたら出る文。あれば、これも出るまで待つ */
  ready?: string
}

/** 準備で作ったデータの名前と番号 */
export interface ScreenData {
  communityId: number
  communityName: string
  eventId: number
  eventTitle: string
}

/**
 * 【何のため】主な画面に出すデータを、API で作る。
 * - オーナーのコミュニティ（member が参加済み、outsider が参加を申請中）
 * - そのコミュニティのイベント（member が申し込み済み）
 * メンバー管理・申込者一覧・お知らせの画面が、空ではなく、中身のある状態で確かめられるようにするため。
 */
export async function prepareScreenData(label: string): Promise<ScreenData> {
  const communityName = `E2E ${label}コミュニティ ${tag()}`
  const eventTitle = `E2E ${label}イベント ${tag()}`
  const owner = await sessionOf('owner')
  const member = await sessionOf('member')
  const outsider = await sessionOf('outsider')
  try {
    const { id: communityId } = await json<{ id: number }>(
      await owner.post('/api/communities', { name: communityName, description: `${label}の確認に使うコミュニティです。` }),
      201,
    )
    // member を参加させる（申請して、オーナーが承認する）
    await approveJoin(owner, member, communityId)
    // outsider は、申請だけして、承認を待っている状態にする（メンバー管理の画面の「参加申請」に出る）
    await json(await outsider.post(`/api/communities/${communityId}/join-requests`), 201)
    const { id: eventId } = await json<{ id: number }>(
      await owner.post(`/api/communities/${communityId}/events`, eventInput({ title: eventTitle, location: 'E2E 会議室' })),
      201,
    )
    // member が申し込む（申込者一覧の画面に出る）
    await json(await member.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)
    return { communityId, communityName, eventId, eventTitle }
  } finally {
    await owner.dispose()
    await member.dispose()
    await outsider.dispose()
  }
}

/** 【何のため】user が参加を申請し、owner が承認する */
async function approveJoin(owner: ApiSession, user: ApiSession, communityId: number) {
  const request = await json<{ id: number }>(await user.post(`/api/communities/${communityId}/join-requests`), 201)
  await json(await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'approved' }))
}

/**
 * 【何のため】主な画面の一覧を返す。ログインしていない人の画面と、ログインした人の画面がある。
 * c と e は、準備で作ったコミュニティとイベントの番号（URL に入れる）
 */
export function mainScreens(d: ScreenData): Screen[] {
  const c = d.communityId
  const e = d.eventId
  return [
    // ---------- ログインしていない人の画面 ----------
    { name: 'トップ（イベント一覧）', path: '/', as: 'guest', heading: 'イベントを探す', ready: d.eventTitle },
    { name: 'コミュニティ一覧', path: '/communities', as: 'guest', heading: 'コミュニティ一覧', ready: d.communityName },
    { name: 'コミュニティ詳細', path: `/communities/${c}`, as: 'guest', heading: d.communityName, ready: d.eventTitle },
    { name: 'イベント詳細', path: `/communities/${c}/events/${e}`, as: 'guest', heading: d.eventTitle, ready: 'E2E 会議室' },
    { name: 'ログイン', path: '/login', as: 'guest', heading: 'ログイン' },
    { name: 'アカウント登録', path: '/signup', as: 'guest', heading: 'アカウント登録' },
    // パスワードを忘れた場合（S-20）と、パスワードの再設定（S-21）。本番では再設定を止めているので、問い合わせの案内が出る
    { name: 'パスワードを忘れた場合', path: '/password/forgot', as: 'guest', heading: 'パスワードを忘れた場合', ready: 'お問い合わせ' },
    { name: 'パスワードの再設定', path: '/password/reset#token=e2e-dummy-token', as: 'guest', heading: 'パスワードの再設定' },
    { name: 'プライバシーポリシー', path: '/privacy', as: 'guest', heading: 'プライバシーポリシー' },
    { name: '利用規約', path: '/terms', as: 'guest', heading: '利用規約' },
    { name: 'ページが見つかりません', path: '/no-such-page', as: 'guest', heading: 'ページが見つかりません' },
    // ---------- メンバー（参加している人）の画面 ----------
    { name: 'トップ（参加予定のイベントあり）', path: '/', as: 'member', heading: 'イベントを探す', ready: d.eventTitle },
    { name: 'コミュニティ詳細（メンバー）', path: `/communities/${c}`, as: 'member', heading: d.communityName, ready: 'あなたの立場：' },
    { name: 'イベント詳細（申込み済み）', path: `/communities/${c}/events/${e}`, as: 'member', heading: d.eventTitle, ready: '申込み済み' },
    { name: 'お知らせ', path: '/notifications', as: 'member', heading: 'お知らせ' },
    { name: 'アカウント設定', path: '/account', as: 'member', heading: 'アカウント設定' },
    // ---------- オーナーの画面（管理の画面） ----------
    { name: '管理しているコミュニティ', path: '/manage/communities', as: 'owner', heading: '管理しているコミュニティ', ready: d.communityName },
    { name: 'コミュニティ作成', path: '/manage/communities/new', as: 'owner', heading: 'コミュニティを作る' },
    { name: 'コミュニティ編集', path: `/manage/communities/${c}/edit`, as: 'owner', heading: 'コミュニティを編集' },
    { name: 'メンバー管理', path: `/manage/communities/${c}/members`, as: 'owner', heading: `メンバー管理：${d.communityName}`, ready: '参加申請' },
    { name: 'イベント作成', path: `/manage/communities/${c}/events/new`, as: 'owner', heading: 'イベントを作る' },
    { name: 'イベント編集', path: `/manage/communities/${c}/events/${e}/edit`, as: 'owner', heading: 'イベントを編集' },
    { name: '申込者一覧', path: `/manage/communities/${c}/events/${e}/applicants`, as: 'owner', heading: `申込者一覧：${d.eventTitle}` },
    { name: '選ぶ画面（コミュニティ編集）', path: '/manage/select/community-edit', as: 'owner', heading: 'コミュニティ編集：コミュニティを選ぶ', ready: d.communityName },
    { name: '選ぶ画面（メンバー管理）', path: '/manage/select/members', as: 'owner', heading: 'メンバー管理：コミュニティを選ぶ', ready: d.communityName },
    { name: '選ぶ画面（イベント作成）', path: '/manage/select/event-create', as: 'owner', heading: 'イベント作成：コミュニティを選ぶ', ready: d.communityName },
    { name: '選ぶ画面（イベント編集）', path: '/manage/select/event-edit', as: 'owner', heading: 'イベント編集：イベントを選ぶ', ready: d.eventTitle },
    { name: '選ぶ画面（申込者一覧）', path: '/manage/select/applicants', as: 'owner', heading: '申込者一覧：イベントを選ぶ', ready: d.eventTitle },
    { name: 'お知らせ（オーナー）', path: '/notifications', as: 'owner', heading: 'お知らせ' },
  ]
}

/**
 * 【何のため】画面を開き、中身が読み込まれ終わるまで待つ。
 * 読み込みの途中（「読み込み中…」）で確かめると、正しい結果にならないため。
 */
export async function openScreen(page: Page, screen: Screen) {
  await page.goto(screen.path)
  // level: 1 は「h1 の見出し」。exact: true は「名前がぴったり同じもの」
  await expect(page.getByRole('heading', { level: 1, name: screen.heading, exact: true })).toBeVisible()
  if (screen.ready) {
    // first()：同じ文が2か所に出ることがあるので、最初の1つを見る
    await expect(page.locator('main').getByText(screen.ready).first()).toBeVisible()
  }
  await expect(page.locator('main').getByText('読み込み中…')).toHaveCount(0)
}
