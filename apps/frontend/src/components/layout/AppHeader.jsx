import {
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  PROFILE_ROUTE,
  SETTINGS_ROUTE,
} from '../../constants/tradingUi.js'

const TABS = [
  { route: DASHBOARD_ROUTE, label: '현황' },
  { route: SETTINGS_ROUTE, label: '매매 설정' },
  { route: PROFILE_ROUTE, label: '계정', authOnly: true },
  { route: ADMIN_USERS_ROUTE, label: '관리자', authOnly: true, adminOnly: true },
]

function AppHeader({
  activeRoute,
  onNavigateRoute,
  authChecking,
  authProviders,
  authError,
  onProviderLogin,
  engineError,
  engineBusy,
  engineStatus,
  engineKnown,
  tradingMode,
  onEngineToggle,
  onPanic,
  panicBusy,
  hasOpenPositions,
  updatedAt,
  authUser,
  connectionClass,
  connectionLabel,
  onLogout,
  approvalStatus,
  canAccessAdmin,
}) {
  const authenticated = Boolean(authUser?.id)
  const userLabel = authenticated
    ? (authUser?.displayName || authUser?.email || `${authUser?.provider}:${authUser?.providerUserId}`)
    : '게스트'
  const providers = Array.isArray(authProviders) ? authProviders.filter(Boolean) : []
  const loginLabel = authChecking ? '확인 중' : providers.length === 0 ? '로그인 설정 필요' : null

  // Three states, not two. Collapsing "unknown" into "OFF" showed a live engine as stopped and
  // offered to start it — the most dangerous thing this header could get wrong.
  const engineDotClass = !engineKnown ? 'engine-dot--unknown' : engineStatus ? 'engine-dot--on' : 'engine-dot--off'
  const engineLabel = !engineKnown ? 'ENGINE ?' : engineStatus ? 'ENGINE ON' : 'ENGINE OFF'

  return (
    <>
      <header className="topbar">
        <span className="topbar__brand">BTC AUTO TRADER</span>
        {/* Which money is at stake is the one thing this bar must never leave ambiguous. */}
        {tradingMode && (
          <span className={`mode-badge ${tradingMode === 'PAPER' ? 'mode-badge--paper' : 'mode-badge--live'}`}>
            {tradingMode === 'PAPER' ? '모의' : '실계좌'}
          </span>
        )}

        <nav className="topbar__nav" aria-label="페이지 이동">
          {TABS.map((tab) => {
            if (tab.authOnly && !authenticated) return null
            if (tab.adminOnly && !canAccessAdmin) return null
            return (
              <button
                key={tab.route}
                type="button"
                className={`route-tab ${activeRoute === tab.route ? 'is-active' : ''}`}
                aria-current={activeRoute === tab.route ? 'page' : undefined}
                onClick={() => onNavigateRoute(tab.route)}
              >
                {tab.label}
              </button>
            )
          })}
        </nav>

        <span className="topbar__spacer" />

        <div className="topbar__meta">
          <span>
            갱신 <b className="mono">{updatedAt}</b>
          </span>
          <span className={`connection-badge ${connectionClass}`}>{connectionLabel}</span>
          {authenticated && (
            <span title={authUser?.email ?? undefined}>
              {userLabel}
              {approvalStatus && approvalStatus !== '-' ? (
                <b className={`approval-status ${approvalStatus === 'ADMIN' ? 'approval-status--admin' : ''}`} style={{ marginLeft: 6 }}>
                  {approvalStatus}
                </b>
              ) : null}
            </span>
          )}
        </div>

        <div className="topbar__actions">
          {authenticated ? (
            <>
              <span className="engine-control">
                <span className={`engine-dot ${engineDotClass}`} aria-hidden="true" />
                <span className="engine-state-label">{engineLabel}</span>
              </span>
              <button
                type="button"
                className="engine-toggle-btn"
                onClick={onEngineToggle}
                disabled={engineBusy}
              >
                {engineBusy ? '처리 중' : engineStatus ? '엔진 중지' : '엔진 시작'}
              </button>
              <button
                type="button"
                className="panic-btn"
                onClick={onPanic}
                disabled={panicBusy}
                title="엔진을 중지하고 보유 코인을 전량 시장가 매도합니다"
              >
                {panicBusy ? '청산 중' : '긴급 청산'}
              </button>
              <button className="ghost-button" type="button" onClick={onLogout}>
                로그아웃
              </button>
            </>
          ) : loginLabel ? (
            <button type="button" className="primary-button" disabled>
              {loginLabel}
            </button>
          ) : (
            <div className="login-provider-list">
              {providers.map((provider, index) => (
                <button
                  key={provider?.id ?? provider?.authorizationUrl ?? index}
                  type="button"
                  className={index === 0 ? 'primary-button' : 'ghost-button'}
                  onClick={() => onProviderLogin(provider?.authorizationUrl)}
                  disabled={authChecking || !provider?.authorizationUrl}
                >
                  {provider?.name ? `${provider.name} 로그인` : '로그인'}
                </button>
              ))}
            </div>
          )}
        </div>
      </header>

      {authenticated && !engineKnown && (
        <p className="status-error">
          엔진 상태를 확인할 수 없습니다. 실제로는 동작 중일 수 있으니 시작/중지 전에 서버 연결을 확인하세요.
        </p>
      )}
      {authenticated && engineError && <p className="status-error">{engineError}</p>}
      {authenticated && !engineStatus && engineKnown && hasOpenPositions && (
        <p className="status-error">
          엔진이 중지된 상태에서 보유 포지션이 있습니다. 손절/트레일링이 동작하지 않습니다.
        </p>
      )}
      {!authenticated && authError && <p className="status-error">{authError}</p>}
    </>
  )
}

export default AppHeader
