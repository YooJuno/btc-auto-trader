import { DASHBOARD_ROUTE, SETTINGS_ROUTE } from '../../constants/tradingUi.js'

function AppHeader({
  activeRoute,
  onNavigateRoute,
  engineClass,
  engineLabel,
  engineError,
  engineBusy,
  engineStatus,
  onEngineStart,
  onEngineStop,
  updatedAt,
  authUser,
  connectionClass,
  connectionLabel,
  onLogout,
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
        </div>
      </div>
      <div className="engine-inline-card">
        <div className="engine-inline-head">
          <strong>자동매매</strong>
          <span className={`status ${engineClass}`}>ENGINE {engineLabel}</span>
        </div>
        {engineError && <p className="status-error">{engineError}</p>}
        <div className="engine-inline-actions">
          <button
            className={`engine-action-btn engine-action-btn--start ${engineStatus ? 'is-active' : ''}`}
            onClick={onEngineStart}
            disabled={engineBusy || engineStatus}
          >
            <span className="engine-action-btn__icon" aria-hidden="true">
              ▶
            </span>
            <span className="engine-action-btn__label">
              {engineBusy && !engineStatus ? '시작 중...' : '엔진 시작'}
            </span>
          </button>
          <button
            className={`engine-action-btn engine-action-btn--stop ${engineStatus ? '' : 'is-active'}`}
            onClick={onEngineStop}
            disabled={engineBusy || !engineStatus}
          >
            <span className="engine-action-btn__icon" aria-hidden="true">
              ■
            </span>
            <span className="engine-action-btn__label">
              {engineBusy && engineStatus ? '중지 중...' : '엔진 중지'}
            </span>
          </button>
        </div>
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
