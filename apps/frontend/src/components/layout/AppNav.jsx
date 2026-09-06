import {
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  PROFILE_ROUTE,
  SETTINGS_ROUTE,
} from '../../constants/tradingUi.js'

/*
 * Left navigation rail.
 *
 * Navigation moved out of the header for two reasons. Tabs crammed next to the engine controls put
 * destructive actions (긴급 청산) and routine ones (계정) on the same row at the same weight, and a
 * horizontal tab strip has nowhere to grow — every new route squeezes the account area further.
 *
 * Icons are inline SVG on a 16px grid. No icon font, no emoji: an emoji renders differently on every
 * platform and reads as decoration in a tool where every other glyph is data.
 */

const Icon = ({ path }) => (
  <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false">
    <path d={path} fill="none" stroke="currentColor" strokeWidth="1.4"
      strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

// Candles for the live view, sliders for settings, person for the account, shield for admin.
const ICONS = {
  dashboard: 'M2 13h12M4.5 10V6M4.5 4.5v1.5M4.5 10v1.5M8 9V4M8 2.5V4M8 9v2M11.5 11V7M11.5 5.5V7M11.5 11v1',
  settings: 'M2 4.5h6M11 4.5h3M2 11.5h3M8 11.5h6M9.5 3v3M6.5 10v3',
  profile: 'M8 8.5a2.75 2.75 0 1 0 0-5.5 2.75 2.75 0 0 0 0 5.5ZM3 13.5c0-2.2 2.2-3.5 5-3.5s5 1.3 5 3.5',
  admin: 'M8 2 3 4v4c0 3 2.1 5.2 5 6 2.9-.8 5-3 5-6V4L8 2Z',
}

const ITEMS = [
  { route: DASHBOARD_ROUTE, label: '현황', icon: 'dashboard' },
  { route: SETTINGS_ROUTE, label: '매매 설정', icon: 'settings' },
  { route: PROFILE_ROUTE, label: '계정', icon: 'profile', authOnly: true },
  { route: ADMIN_USERS_ROUTE, label: '관리자', icon: 'admin', authOnly: true, adminOnly: true },
]

function AppNav({ activeRoute, onNavigateRoute, authenticated, canAccessAdmin }) {
  const items = ITEMS.filter((item) => {
    if (item.authOnly && !authenticated) return false
    if (item.adminOnly && !canAccessAdmin) return false
    return true
  })

  return (
    <nav className="nav" aria-label="주요 메뉴">
      <ul className="nav__list">
        {items.map((item) => {
          const active = activeRoute === item.route
          return (
            <li key={item.route}>
              <button
                type="button"
                className={`nav__item ${active ? 'is-active' : ''}`}
                aria-current={active ? 'page' : undefined}
                onClick={() => onNavigateRoute(item.route)}
              >
                <Icon path={ICONS[item.icon]} />
                <span className="nav__label">{item.label}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

export default AppNav
