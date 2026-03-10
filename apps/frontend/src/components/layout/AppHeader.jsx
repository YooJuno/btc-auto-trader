import {
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  SETTINGS_ROUTE,
} from '../../constants/tradingUi.js'

function AppHeader({
  activeRoute,
  onNavigateRoute,
  authChecking,
  authProviders,
  authError,
  onProviderLogin,
  engineClass,
  engineError,
  engineBusy,
  engineStatus,
  onEngineToggle,
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
    ? (authUser?.email || authUser?.displayName || `${authUser?.provider}:${authUser?.providerUserId}`)
    : '게스트'
  const approvalStatusLabel = approvalStatus || '-'
  const approvalStatusClass = approvalStatusLabel === 'ADMIN' ? 'approval-status--admin' : ''
  const providers = Array.isArray(authProviders) ? authProviders.filter(Boolean) : []
  const loginLabel = authChecking ? '로그인 확인 중...' : providers.length === 0 ? '로그인 설정 필요' : null

  return (
    <header className="app__header">
      <div className="brand-block">
        <p className="eyebrow">BTC AUTO TRADER</p>
        <h1>Trading Control Center</h1>
        <p className="sub">
          실시간 상태는 메인에서 확인하고, 매매 세팅은 로그인 없이 조회할 수 있습니다.
        </p>
        <div className="route-tabs" role="tablist" aria-label="페이지 이동">
          <button
            type="button"
            className={`route-tab ${activeRoute === DASHBOARD_ROUTE ? 'is-active' : ''}`}
            onClick={() => onNavigateRoute(DASHBOARD_ROUTE)}
          >
            실시간 현황
          </button>
          <button
            type="button"
            className={`route-tab ${activeRoute === SETTINGS_ROUTE ? 'is-active' : ''}`}
            onClick={() => onNavigateRoute(SETTINGS_ROUTE)}
          >
            매매 세팅
          </button>
          {authenticated && canAccessAdmin && (
            <button
              type="button"
              className={`route-tab ${activeRoute === ADMIN_USERS_ROUTE ? 'is-active' : ''}`}
              onClick={() => onNavigateRoute(ADMIN_USERS_ROUTE)}
            >
              관리자
            </button>
          )}
          {authenticated ? (
            <div className="route-tabs-tools">
              <span className={`status ${engineClass}`}>ENGINE {engineStatus ? 'ON' : 'OFF'}</span>
              <button
                type="button"
                className={`engine-toggle-btn ${engineStatus ? 'is-on' : 'is-off'}`}
                onClick={onEngineToggle}
                disabled={engineBusy}
              >
                {engineBusy ? (engineStatus ? '중지 중...' : '시작 중...') : engineStatus ? '엔진 중지' : '엔진 시작'}
              </button>
            </div>
          ) : (
            <div className="route-tabs-tools">
              {loginLabel ? (
                <button
                  type="button"
                  className="primary-button"
                  disabled
                >
                  {loginLabel}
                </button>
              ) : (
                <div className="login-provider-list">
                  {providers.map((provider, index) => {
                    const providerLabel = provider?.name ? `${provider.name} 로그인` : '로그인'
                    return (
                      <button
                        key={provider?.id ?? provider?.authorizationUrl ?? providerLabel}
                        type="button"
                        className={index === 0 ? 'primary-button' : 'ghost-button'}
                        onClick={() => onProviderLogin(provider?.authorizationUrl)}
                        disabled={authChecking || !provider?.authorizationUrl}
                      >
                        {providerLabel}
                      </button>
                    )
                  })}
                </div>
              )}
            </div>
          )}
        </div>
        {authenticated && engineError && <p className="status-error">{engineError}</p>}
        {!authenticated && authError && <p className="status-error">{authError}</p>}
      </div>
      <div className="status-card">
        <div className="status-row">
          <span>업데이트</span>
          <strong className="mono">{updatedAt}</strong>
        </div>
        <div className="status-row">
          <span>사용자</span>
          <strong className="mono">{userLabel}</strong>
        </div>
        <div className="status-row">
          <span>승인 상태</span>
          <strong className={`mono approval-status ${approvalStatusClass}`}>{approvalStatusLabel}</strong>
        </div>
        <div className="status-connection-row">
          <span>서버 연결</span>
          <span className={`connection-badge ${connectionClass}`}>{connectionLabel}</span>
        </div>
        <div className="status-actions">
          {authenticated ? (
            <button className="ghost-button" type="button" onClick={onLogout}>
              로그아웃
            </button>
          ) : (
            <p className="sub compact">로그인 후 저장/주문/엔진 제어가 활성화됩니다.</p>
          )}
        </div>
      </div>
    </header>
  )
}

export default AppHeader
