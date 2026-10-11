/**
 * 【役割】URL と画面の対応表。
 * 【なぜ必要か】「この URL を開いたら、この画面を表示する」というルールを1か所にまとめるため。
 * URL は docs/screens.md の「2. 画面の一覧」と同じにしている。画面を増やすときは、両方を直す。
 */

// createBrowserRouter：URL と画面の対応表から、画面を切り替える仕組み（ルーター）を作る関数
import { createBrowserRouter } from 'react-router'
// すべての画面に共通する枠（画面上部のメニューと、画面下部のリンク）
import Layout from './components/Layout'
// ログインが必要な画面を守る部品
import RequireAuth from './components/RequireAuth'
// 各画面。docs/screens.md のまとまり（利用者・管理・共通）ごとに並べている
// 利用者の画面
import EventListPage from './pages/EventListPage'
import CommunityListPage from './pages/CommunityListPage'
import CommunityDetailPage from './pages/CommunityDetailPage'
import EventDetailPage from './pages/EventDetailPage'
import AccountSettingsPage from './pages/AccountSettingsPage'
// 管理の画面
import ManagedCommunityListPage from './pages/ManagedCommunityListPage'
import CommunityCreatePage from './pages/CommunityCreatePage'
import CommunityEditPage from './pages/CommunityEditPage'
import MemberManagementPage from './pages/MemberManagementPage'
import EventCreatePage from './pages/EventCreatePage'
import EventEditPage from './pages/EventEditPage'
import ApplicantListPage from './pages/ApplicantListPage'
import SelectCommunityPage from './pages/SelectCommunityPage'
import SelectEventPage from './pages/SelectEventPage'
// 共通の画面
import SignupPage from './pages/SignupPage'
import LoginPage from './pages/LoginPage'
import NotificationListPage from './pages/NotificationListPage'
import PrivacyPolicyPage from './pages/PrivacyPolicyPage'
import TermsPage from './pages/TermsPage'
import NotFoundPage from './pages/NotFoundPage'

/**
 * 【何のため】アプリ全体で使うルーターを作る。
 * path が URL、Component がその URL で表示する画面。
 * 「:communityId」のように「:」で始まる部分には、実際の番号が入る。
 */
export const router = createBrowserRouter([
  {
    // すべての画面を、共通の枠（Layout）の中に表示する
    path: '/',
    Component: Layout,
    // children に書いた画面が、Layout の中の <Outlet /> の場所に表示される
    children: [
      // ---------- 利用者の画面 ----------
      // index: true は「親と同じ URL（/）のときに表示する画面」という意味
      { index: true, Component: EventListPage },                                         // S-01
      { path: 'communities', Component: CommunityListPage },                             // S-17
      { path: 'communities/:communityId', Component: CommunityDetailPage },              // S-05
      { path: 'communities/:communityId/events/:eventId', Component: EventDetailPage },  // S-09

      // ---------- ログインが必要な画面 ----------
      // path を書かずに Component だけを書くと、URL は変えずに、子の画面を RequireAuth で包める。
      // ログインしていない人が来たら、RequireAuth がログインの画面へ移動させる
      {
        Component: RequireAuth,
        children: [
          { path: 'account', Component: AccountSettingsPage },        // S-12
          { path: 'notifications', Component: NotificationListPage }, // S-15

          // ---------- 管理の画面（URL は /manage で始まる） ----------
          {
            // path: 'manage' の下に children を書くと、子の URL の頭に「manage/」が付く。
            // 例：子の 'communities' は、/manage/communities になる
            path: 'manage',
            children: [
              { path: 'communities', Component: ManagedCommunityListPage },                      // S-16
              // 「new」は「:communityId」より前に書いておく。/manage/communities/new を作成画面として扱うため
              { path: 'communities/new', Component: CommunityCreatePage },                       // S-04
              { path: 'communities/:communityId/edit', Component: CommunityEditPage },           // S-06
              { path: 'communities/:communityId/members', Component: MemberManagementPage },     // S-07
              { path: 'communities/:communityId/events/new', Component: EventCreatePage },       // S-08
              { path: 'communities/:communityId/events/:eventId/edit', Component: EventEditPage },            // S-10
              { path: 'communities/:communityId/events/:eventId/applicants', Component: ApplicantListPage },  // S-11
              // 左のメニューから開く「対象を選ぶ」画面（Issue #45）。同じ画面を、何をするか（purpose）を変えて使う。
              // element は、Component の代わりに「値を渡した画面」を書くときの書き方
              { path: 'select/community-edit', element: <SelectCommunityPage purpose="community-edit" /> },  // S-18
              { path: 'select/members', element: <SelectCommunityPage purpose="members" /> },                // S-18
              { path: 'select/event-create', element: <SelectCommunityPage purpose="event-create" /> },      // S-18
              { path: 'select/event-edit', element: <SelectEventPage purpose="event-edit" /> },              // S-19
              { path: 'select/applicants', element: <SelectEventPage purpose="applicants" /> },              // S-19
            ],
          },
        ],
      },

      // ---------- 共通の画面 ----------
      { path: 'signup', Component: SignupPage },                  // S-02
      { path: 'login', Component: LoginPage },                    // S-03
      { path: 'privacy', Component: PrivacyPolicyPage },          // S-13
      { path: 'terms', Component: TermsPage },                    // S-14
      // 「*」は「上のどれにも当てはまらない URL」という意味
      { path: '*', Component: NotFoundPage },                     // S-99
    ],
  },
])
