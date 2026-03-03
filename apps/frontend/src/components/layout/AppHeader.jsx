import {
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  SETTINGS_ROUTE,
} from '../../constants/tradingUi.js'

function AppHeader({
  activeRoute,
  onNavigateRoute,
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
  const userLabel =
    authUser?.email || authUser?.displayName || `${authUser?.provider}:${authUser?.providerUserId}`

  return (
    <header className="app__header">
      <div className="brand-block">
        <p className="eyebrow">BTC AUTO TRADER</p>
        <h1>Trading Control Center</h1>
        <p className="sub">실시간 상태는 메인에서 확인하고, 전략/거래 설정은 설정 페이지에서 관리합니다.</p>
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
          {canAccessAdmin && (
            <button
              type="button"
              className={`route-tab ${activeRoute === ADMIN_USERS_ROUTE ? 'is-active' : ''}`}
              onClick={() => onNavigateRoute(ADMIN_USERS_ROUTE)}
            >
              관리자
            </button>
          )}
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
        </div>
        {engineError && <p className="status-error">{engineError}</p>}
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
          <strong className="mono">{approvalStatus || '-'}</strong>
        </div>
        <div className="status-connection-row">
          <span>서버 연결</span>
          <span className={`connection-badge ${connectionClass}`}>{connectionLabel}</span>
        </div>
        <div className="status-actions">
          <button className="ghost-button" type="button" onClick={onLogout}>
            로그아웃
          </button>
        </div>
      </div>
    </header>
  )
}

export default AppHeader
