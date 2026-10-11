/**
 * 【役割】E2E テストの最初に、1回だけ行う準備。テスト用の利用者を3人作り、ログインした状態を保存する。
 * 【なぜ必要か】API のテストと、画面のテストが、同じ利用者を使い回せるようにするため
 * （本番の回数制限に収まるように、登録とログインの回数を少なくする）。
 *
 * - owner：コミュニティを作り、管理する人
 * - member：コミュニティに参加する人
 * - outsider：コミュニティに参加しない人（メンバー以外の申込み、権限のチェックに使う）
 */
import { signupViaApi, startRun } from './support/accounts'
import { BASE_URL } from './support/env'

export default async function globalSetup() {
  const id = startRun()
  console.log(`E2E：${BASE_URL} をテストします（実行の番号 ${id}）`)
  for (const role of ['owner', 'member', 'outsider'] as const) {
    const session = await signupViaApi(role)
    await session.dispose()
  }
}
